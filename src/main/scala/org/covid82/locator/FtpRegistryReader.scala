package org.covid82.locator

import cats.effect.{Async, ContextShift}
import fs2.Stream
import ray.fs2.ftp.Ftp
import ray.fs2.ftp.Ftp.connect
import ray.fs2.ftp.settings.FtpCredentials.credentials
import ray.fs2.ftp.settings.FtpSettings

import scala.concurrent.{ExecutionContextExecutor => ECE}

case class FtpConfig(
  path: String,
  host: String,
  port: Int,
  user: String,
  pass: String
)

class FtpRegistryReader[F[_]](config: FtpConfig) extends RegistryReader[F] {
  protected def read(implicit ec: ECE, cs: ContextShift[F], as: Async[F]): Stream[F, String] = {
    val credential = credentials(config.user, config.pass)
    val settings = FtpSettings(config.host, config.port, credential)
    for {
      client <- Stream.resource(connect[F](settings))
      stream <- Ftp.readFile[F](config.path)(client)
        .through(fs2.text.utf8Decode)
        .through(fs2.text.lines)
    } yield stream
  }
}