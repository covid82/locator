package org.covid82.locator

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Sync, Timer}
import fs2.Stream
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._
import cats.syntax.semigroupk._
import AppRoutes._
import natchez.EntryPoint
import fs2.concurrent.SignallingRef
import cats.effect.syntax.concurrent._
import cats.syntax.apply._

object AppServer {

  def stream[F[_] : ConcurrentEffect : ContextShift : Timer](
    registryReader: RegistryReader[F],
    registryRef: SignallingRef[F, Option[IpRegistry]],
    watcher: Stream[F, RegistryUpdated]
  )(implicit
    blocker: Blocker,
    entryPoint: EntryPoint[F]
  ): Stream[F, Nothing] = {
    for {
      ripeService <- Stream(RipeService[F](registryReader, registryRef))
      _ <- watcher.evalMap(m => (ripeService.read *> Sync[F].delay(println(m))).start)
      _ <- Stream.eval(ripeService.read.start)
      routes = monitoringRoutes[F](ripeService) <+>
        staticFilesRoute(blocker) <+>
        apiRoutes[F](ripeService)
      exitCode <- BlazeServerBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(routes.orNotFound)
        .serve
    } yield exitCode
  }.drain
}