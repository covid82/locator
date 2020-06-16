package org.covid82.locator

import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.{Async, ContextShift, Resource, Sync}
import fs2.{Pipe, Stream}
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
  def ftp[F[_]](config: FtpConfig): RegistryReader[F] =
    new FtpRegistryReader[F](config)

  def db[F[_] : Async : ContextShift](config: DbConfig): RegistryReader[F] =
    new DbRegistryReader[F](config)
}