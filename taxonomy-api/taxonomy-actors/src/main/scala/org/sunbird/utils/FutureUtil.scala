package org.sunbird.utils

import scala.concurrent.{ExecutionContext, Future}

object FutureUtil {

  def sequentially[A, B](items: List[A])(f: A => Future[B])(implicit ec: ExecutionContext): Future[List[B]] =
    items.foldLeft(Future.successful(List.empty[B])) { (accFut, item) =>
      accFut.flatMap(acc => f(item).map(b => acc :+ b))
    }
}
