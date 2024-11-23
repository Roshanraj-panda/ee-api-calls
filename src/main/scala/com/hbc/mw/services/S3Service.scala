package com.hbc.mw.services

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification
import com.hbc.mw.configs.AppConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, S3Exception}
import zio.s3._
import zio.stream.{ZPipeline, ZStream}
import zio.{Layer, ZIO, ZLayer}

import java.net.{URI, URLEncoder}



trait S3Service {
  def listBucketL(config:AppConfig) : ZStream[S3, S3Exception,S3ObjectSummary]
  def downloadKey(summery:S3ObjectSummary):ZStream[S3, Exception, String]
  def downloadKey(event:S3EventNotification.S3Entity):ZStream[S3, Exception, String]

  def archive(bucket:String, key:String, archive:String):ZIO[S3, S3Exception, Unit]
  //def uploadFileFromSFTP(content:ZStream[ftp.SFtp,Throwable,Byte],fileName:String):ZIO[S3 with ftp.SFtp, S3Exception, Unit]
}

case class S3Action(config:AppConfig) extends S3Service {
  override def listBucketL(config: AppConfig): ZStream[S3, S3Exception, S3ObjectSummary] = {
    listAllObjects(config.bucketName,ListObjectOptions(Some(config.prefix),5,None,None)).filterNot(s => s.key.endsWith("/"))
  }
  override def downloadKey(summery:S3ObjectSummary): ZStream[S3, Exception, String] =
    getObject(summery.bucketName,summery.key).via(ZPipeline.utf8Decode >>> ZPipeline.splitLines).drop(1)

  override def downloadKey(event: S3EventNotification.S3Entity): ZStream[S3, Exception, String] =
    getObject(event.getBucket.getName,event.getObject.getKey).via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)

  /*override def uploadFileFromSFTP(content:ZStream[ftp.SFtp,Throwable,Byte],fileName:String): ZIO[S3 with ftp.SFtp, S3Exception, Unit] = multipartUpload(config.bucketName,
    config.prefix + "/" + fileName,
    content,
    MultipartUploadOptions.fromUploadOptions(UploadOptions.fromContentType("text/plain")))(4)*/

  override def  archive(bucket:String, key:String, archive:String): ZIO[S3, S3Exception, Unit] = {
    for {
      _ <- execute(e => e.copyObject(
      CopyObjectRequest
        .builder()
        .copySource(bucket + "/" + key )
        .destinationBucket(bucket)
        .destinationKey(replacePrefix(key,archive))
        .build()
      )
      )

      _ <- deleteObject(bucket,key)

    } yield()
  }

  private def replacePrefix(key: String, newPrefix: String): String = s"""${newPrefix}${key.split("/").last}"""

}

object S3Service {
  val s3ActionLive: ZLayer[AppConfig, Nothing, S3Action] = ZLayer.fromFunction(S3Action.apply _)
  val s3Live = liveZIO(Region.US_EAST_1,zio.s3.providers.default )
  def s3LiveWithAccessKey(key:String, secret:String,uri:Option[URI]): Layer[S3Exception, S3] = live(Region.AWS_GLOBAL,AwsBasicCredentials.create(key, secret),uri)
}
