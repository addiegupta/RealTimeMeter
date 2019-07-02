
package com.server.provision

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.server.provision.MediatorActor._
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object PlanDbActor{
    def props(implicit materializer: ActorMaterializer, system : ActorSystem) =
        Props(classOf[PlanDbActor],materializer,system)

    case class FindPlanById(id:Int)

    case class UpdateBalanceById(id:Int,data_balance:Int)
    //  system.registerOnTermination(sess.close())

}

class PlanDbActor(implicit materializer: ActorMaterializer, system : ActorSystem) extends Actor
    with ActorLogging {


    val db = Database.forConfig("sample")

    import com.server.provision.PlanDbActor._

//    val newPostgresDb = Database.forConfig("custom.postgres")

//        val databaseConfig = DatabaseConfig.forConfig[PostgresProfile]("slick-postgres")
//    val db = Database.forConfig("slick-postgres")
//    val db = HikariCPJdbcDataSource.forConfig("slick-postgres",)

    //    implicit val sess = SlickSession.forConfig(databaseConfig)

    //    import sess.profile.api._
    implicit val ec = system.dispatcher
    //  val medActor: ActorRef = context.actorOf(Med.props, "medActor")
    class Plans(tag: Tag) extends Table[(Int,String, String, Int)](tag, "plans") {
        def id = column[Int]("id", O.PrimaryKey,O.AutoInc) // This is the primary key column
        def number = column[String]("number")
        def name = column[String]("name")
        def data_balance = column[Int]("data_balance")
        // Every table needs a * projection with the same type as the table's type parameter
        def * = (id,number,name,data_balance)
    }
    val plans = TableQuery[Plans]
    override def receive: Receive = {

        case FindPlanById(id:Int)=>
            log.info(s"FindPlanById called for id: $id")
/*
            val action = plans.filter(_.id === id).map(u => (u.data_balance)).result.map(_.headOption.map {
                case (data_balance) => data_balance
            })
//            val f: Future[Option[Int]] = databaseConfig.db.run(action)
            val f: Future[Option[Int]] = newPostgresDb.run(action)
//            val f: Future[Option[Int]] = db.run(action)

//            f.pipeTo(sender())
            */

            val action = plans.filter(_.id===id).map(u => (u.data_balance)).result.map(_.headOption.map{
                case data_balance => data_balance
            })

            val queryResult = db.run(action)

//            Await.result(queryResult,Duration.apply(5,"s")).foreach(println)

            sender()! ReplyToMeter(queryResult,id)


        //        f.onComplete {
        //          case s => println(s"Result: $s")
        //                  sender()! ReplyToMeter
        ////                    medActor! ReplyToMeter
        //        }

        case UpdateBalanceById(id:Int,balance:Int)=>
            log.info(s"UpdateBalanceById called for id: $id and balance: $balance")
            val query = for { p <- plans if p.id === id } yield p.data_balance
            val updateAction = query.update(balance)

            // Get the statement without having to specify an updated value:
            //    val sql = query.updateStatement

            db.run(updateAction)
//            sess.db.run(updateAction)
    }
}
