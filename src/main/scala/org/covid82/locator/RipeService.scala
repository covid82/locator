package org.covid82.locator

import cats.effect.{ContextShift, Effect, Timer}
import fs2.Stream
import cats.syntax.apply._
import fs2.concurrent.SignallingRef
import cats.syntax.functor._
import cats.instances.bigInt._

case class RipeService[F[_] : Effect : ContextShift : Timer](
  registryReader: RegistryReader[F],
  registryRef: SignallingRef[F, Option[IpRegistry]]) {

  def read: F[Int] = {
    for {
      records <- registryReader.readRows.fold(List.empty[IpRecord]) {
        case (acc, record) => (record.range, record.cc) :: acc
      }
      _ <- Stream.eval(registryRef.set(Option(records.sorted.toVector)))
    } yield ()
  }.compile.drain *> registryRef.get.map {
    case None => 0
    case Some(registry) => registry.size
  }

  def find(ip: String): F[Option[String]] = registryRef.get.map {
    case None => Option.empty[String]
    case Some(registry) => binarySearch(registry)(RipeRecord.ipToBigInt(ip))
  }
}