package org.sunbird.parseq


import scala.concurrent.{ExecutionContext, Future}

object Task {

    def series[R](block1: => R, block2: => R)(implicit ec: ExecutionContext): Future[List[R]] = {
        for {
            result1 <- Future { block1 }
            result2 <- Future { block2 }
        } yield (List(result1, result2))
    }

    def parallel[R](block1: => Future[R], block2: => Future[R])(implicit ec: ExecutionContext): Future[List[R]] = {
        val r1 = Future { block1 };
        val r2 = Future { block2 };
        val result = for {
            result1 <- r1
            result2 <- r2
        } yield (List(result1, result2))
        result.map(futures => {
            Future.sequence(futures)
        }).flatMap(f => f)
    }

}
