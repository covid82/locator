package org.covid82.locator

import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.{Async, ContextShift, Resource, Sync}
import fs2.{Pipe, Stream}
import ray.fs2.ftp.Ftp
import ray.fs2.ftp.Ftp.connect
import ray.fs2.ftp.settings.FtpCredentials.credentials
import ray.fs2.ftp.settings.FtpSettings

import scala.concurrent.{ExecutionContext => EC, ExecutionContextExecutor => ECE}

trait RegistryReader[F[_]] {

  protected def read(implicit ec: ECE, cs: ContextShift[F], a: Async[F]): Stream[F, String]

  private def dropEmptyLinesAndComments: Pipe[F, String, String] = _.filter(_.trim.nonEmpty).filter(!_.startsWith("#"))

  private def dropHeader: Pipe[F, String, String] = _.drop(1)

  private def splitAndDropSummary: Pipe[F, String, List[String]] = _
    .map(s => s.split('|').toList)
    .filter(s => s(1) != "*")

  private def executionContext(
    implicit F: Sync[F]
  ): Resource[F, (ExecutorService, ECE)] = Resource.make(F.delay {
    val ec: ExecutorService = Executors.newSingleThreadExecutor()
    val ece: ECE = EC.fromExecutor(ec)
    (ec, ece)
  }) { case (service, _) => F.delay(service.shutdown()) }

  private def createRows: Pipe[F, List[String], RipeRecord] = _.collect {
    case registry :: cc :: typ :: start :: value :: date :: status :: extensions =>
      RipeRecord(registry, cc, typ, start, value, date, status, extensions.fold("")(_ + "|" + _))
  }

  def readRows(implicit async: Async[F], cs: ContextShift[F]): Stream[F, RipeRecord] = for {
    ec <- Stream.resource(executionContext).map { case (_, ec) => ec }
    rs <- read(ec, cs, async)
      .through(dropEmptyLinesAndComments)
      .through(dropHeader)
      .through(splitAndDropSummary)
      .through(createRows)
      .filter(r => Set("ipv4" /*, "ipv6"*/).contains(r.typ))
  } yield rs
}

object RegistryReader {
  def apply[F[_]](config: FtpConfig): RegistryReader[F] = new FtpRegistryReader[F](config)
}

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