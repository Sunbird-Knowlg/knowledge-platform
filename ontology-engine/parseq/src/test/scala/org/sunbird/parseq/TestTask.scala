package org.sunbird.parseq




import scala.concurrent.{ExecutionContext, Future}

object TestTask {

    implicit val ec: ExecutionContext = ExecutionContext.global


    def test(data: String, time: Long) = {
        println(Thread.currentThread().getName + " :: Executing the " + data + " function.")
        Thread.sleep(time)
        println("slept for " + time + " milliseconds : " + System.currentTimeMillis())
        Future(time)
    }

    def main(args: Array[String]): Unit = {
//        testParallel()
        testSeries()
    }

    def testSeries() = {
        Task.series(test("first", 2000), test("second", 3000))
        Thread.sleep(6000)
    }

    def testParallel(): Unit = {
        Task.parallel(test("first", 2000), test("second", 2000))
        Thread.sleep(6000)
    }


}
