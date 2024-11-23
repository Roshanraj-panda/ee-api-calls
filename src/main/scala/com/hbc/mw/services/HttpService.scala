package com.hbc.mw.services

import com.hbc.mw.configs.AppConfig
import zio.http.Header.AuthenticationScheme.Bearer
import zio.http.Header.Authorization
import zio.http._
import zio.schema.{DeriveSchema, Schema}
import zio.{Ref, ULayer, ZIO, ZLayer}
import zio.json._

import java.time.{LocalDateTime, ZoneId}
import java.util.concurrent.atomic.AtomicReference


trait HttpService {
  def request(settings: AppConfig, bdy: String, secret: String): ZIO[Client, Throwable, (String, String)]

  //def get(url: String, settings: AppConfig, hastKey: String): ZIO[Client, Throwable, Response]
}

object HttpService {
  val layer: ULayer[HttpService] = ZLayer.succeed {
    new HttpService {
      override def request(settings: AppConfig, bdy: String, token: String): ZIO[Client, Throwable, (String, String)] = {
        //val header = Headers(Header.Custom("X-EES-AUTH-CLIENT-ID", settings.apiKey), Header.Custom("X-EES-AUTH-HASH", token), Header.ContentType(MediaType.application.`json`))
        val headers = Headers(Authorization.Bearer.apply(token),Header.ContentType(MediaType.application.`json`) )
        Client.request(settings.url, Method.POST, headers, content = Body.fromString(bdy)).tap(r => zio.Console.printLine(s"received response ${r.status.code} for request ${bdy}")).map(r => (r.status.text -> bdy))

      }

      /*override def get(url: String, settings: AppConfig, hastKey: String): ZIO[Client, Throwable, Response] = {
        val header = Headers(Header.Custom("X-EES-AUTH-CLIENT-ID", settings.apiKey), Header.Custom("X-EES-AUTH-HASH", hastKey))
        Client.request(url, Method.GET, header)
      }*/
    }
  }
}

case class Token(access_token:String, expire_in:Long=0L, token_type:String)
object Token {
  implicit val schema: Schema[Token] = DeriveSchema.gen
  implicit val encoder: JsonEncoder[Token] = DeriveJsonEncoder.gen[Token]
  implicit val decoder: JsonDecoder[Token] = DeriveJsonDecoder.gen[Token]
}

case class TokenStatus(tokenDetails:Token, issuedAt:LocalDateTime= LocalDateTime.now()) {
  def getExporationTime = issuedAt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli + tokenDetails.expire_in * 1000
}

object TokenStatus {
  implicit val schema: Schema[TokenStatus] = DeriveSchema.gen
  implicit val encoder: JsonEncoder[TokenStatus] = DeriveJsonEncoder.gen[TokenStatus]
  implicit val decoder: JsonDecoder[TokenStatus] = DeriveJsonDecoder.gen[TokenStatus]
  //implicit val jsonCodec: zio.json.JsonCodec[TokenStatus] =
    //zio.schema.codec.JsonCodec.jsonCodec(schema)
}



trait OauthManager {
  //def getToken(settings: AppConfig)
  def checkTokenExpiry(token:TokenStatus):Boolean
  def generateToken(settings: AppConfig):ZIO[Any with Client, Any, Option[TokenStatus]]
}

case class TokenManager(settings:AppConfig) extends OauthManager {

  val Currenttoken:AtomicReference[Option[TokenStatus]] =  new AtomicReference(None)

  override def generateToken(settings: AppConfig): ZIO[Any with Client, Any, Option[TokenStatus]] = {
    val h = Headers(Header.Custom("grant_tyoe",settings.grantType), Header.Custom("scope",settings.apiScope), Header.Authorization.Basic.apply(settings.accessId, settings.accessSecret))
    val res = Client.request(settings.oAuthDomain, Method.POST,h)
    for {
      response <- res
      t <- response.body.asString.map(_.fromJson[TokenStatus])
      res <- ZIO.fromEither(t)
    } yield {
      Currenttoken.updateAndGet(_ => Option(res) )
    }
  }

  override def checkTokenExpiry(token:TokenStatus): Boolean = {
    val ctime = System.currentTimeMillis()
    token.getExporationTime > ctime
  }

   def getToken(settings: AppConfig): ZIO[Any with Client, Any, Option[TokenStatus]] = Currenttoken.get() match {
    case t @ Some(_) => ZIO.attempt(t)
    case _ => generateToken(settings)

  }
}

object TokenManager {
  val live: ZLayer[AppConfig, Nothing, TokenManager] = ZLayer.fromFunction(TokenManager.apply _)
}