package com.hbc.mw.configs

import zio.{Config, Scope, ZIO, ZLayer}

case class AppConfig(bucketName: String,
                     archivePrefix:String,
                     prefix:String,
                     oAuthDomain:String,
                     accessId:String,
                     accessSecret:String,
                     grantType:String,
                     apiScope:String,
                     url:String,
                     apiKey:String,
                     apiId:String
                    )

/**
 *
 */
object AppConfig {
  val config: Config[AppConfig] = {
    (Config.string("BUCKET_NAME")
      ++ Config.string("ARCHIVE_PREFIX")
      ++ Config.string("FILE_PREFIX")
      ++ Config.string("OAUTH_DOMAIN")
      ++ Config.string("ACCESS_ID")
      ++ Config.string("ACCESS_SECRET")
      ++ Config.string("GRANT_TYPE")
      ++ Config.string("API_SCOPE")
      ++ Config.string("URL")
      ++ Config.string("API_KEY")
      ++ Config.string("API_ID")
      ).map {
      case (bucket, archiveP, prefix,od,aid,asec,gt,scop,url,akey,apiid) => AppConfig(bucket, archiveP,prefix,od,aid,asec,gt,scop,url,akey,apiid)

    }
  }

  val live: ZLayer[Any, Config.Error, AppConfig] = ZLayer.fromZIO(ZIO.config(config))
}


