package org.covid82.locator

import fs2.Stream
import cats.effect.{Async, Blocker, ContextShift}
import doobie.implicits._
import doobie.util.{ExecutionContexts, fragment}
import doobie.util.transactor.Transactor
import doobie.util.transactor.Transactor.Aux

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContextExecutor => ECE}

case class DbConfig(
  url: String,
  driver: String,
  user: String,
  pass: String,
  delay: FiniteDuration
)

class DbRegistryReader[F[_]](config: DbConfig) extends RegistryReader[F] {

  val selectQueryFragment: fragment.Fragment =
    sql"SELECT registry, cc, typ, start, value, date, status, extensions FROM row"

  private def xa(implicit as: Async[F], cs: ContextShift[F]): Aux[F, Unit] = Transactor.fromDriverManager[F](
    url = config.url,
    driver = config.driver,
    user = config.user,
    pass = config.pass,
    blocker = Blocker.liftExecutionContext(ExecutionContexts.synchronous)
  )

  override protected def read(
    implicit ec: ECE, cs: ContextShift[F], as: Async[F]
  ): Stream[F, RipeRecord] =
    selectQueryFragment
      .query[RipeRecord]
      .stream
      .transact[F](xa)
}