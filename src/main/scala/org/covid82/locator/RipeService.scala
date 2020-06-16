package org.covid82.locator

import cats.effect.{ContextShift, Effect, Timer}
import fs2.Stream

class RipeService[F[_] : Effect : ContextShift : Timer](config: FtpConfig) {
  val ripe: RegistryReader[F] = RegistryReader[F](config)

  def read: Stream[F, IpRegistry] =
    ripe.readRows.fold(List.empty[IpRecord]) {
      case (acc, record) => (record.range, record.cc) :: acc
    }.map(_.sorted.toVector)
}

object RipeService {
  def apply[F[_] : Effect : ContextShift : Timer](config: FtpConfig) = new RipeService[F](config)
}