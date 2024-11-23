package com.hbc.mw

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.hbc.mw.configs.AppConfig
import com.hbc.mw.services.{HttpService, S3Action, S3Service, TokenManager}
import zio.http.DnsResolver
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver
import zio.stream.ZStream
import zio.{Chunk, Scope, Unsafe, ZIO, ZLayer}
import zio.http.{Client, DnsResolver, ZClient}

import scala.jdk.CollectionConverters.CollectionHasAsScala

object Main extends RequestHandler[S3Event,String] {

  override def handleRequest(input: S3Event, context: Context): String = {

    val clientConfig = ZClient.Config.default

    val app: ZIO[Any, Throwable, Chunk[(String, String)]] = {


      for {
        logger <- ZIO.succeed(context.getLogger)
        _ <- ZIO.succeed(logger.log("starting lambda execution"))
        config <- ZIO.service[AppConfig]
        s3Serv <- ZIO.service[S3Action]
        httpserv <- ZIO.service[HttpService]
        tmanager <- ZIO.service[TokenManager]
        _ <- ZIO.succeed(logger.log(s"received s3 events ${input.getRecords.asScala.mkString(",")}"))
        events <- ZIO.succeed(input.getRecords.asScala.toList.map(_.getS3))
        responses <- ZStream.fromIterable(events).flatMap(s3Serv.downloadKey).mapZIO(r => httpserv.request(config,r,tmanager.Currenttoken.get().get.tokenDetails.access_token)).runCollect

      } yield (responses)
    }.provide(S3Service.s3Live, S3Service.s3ActionLive,AppConfig.live,Scope.default, TokenManager.live, HttpService.layer,Client.customized,
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(clientConfig),
      ZLayer.succeed(NettyConfig.default)
    )



    Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(
        app *> ZIO.succeed("Lambda execution completed")
      ).getOrThrowFiberFailure()
    }
  }
}
