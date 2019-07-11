
package com.server.provision

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.server.provision.MediatorActor._
import slick.jdbc.PostgresProfile.api._

object PlanDbActor{
    def props(implicit system : ActorSystem) =
        Props(classOf[PlanDbActor],system)

    case class FindPlanById(id:Int)

    case class UpdateBalanceById(id:Int,data_balance:Int)

}
class PlanDbActor(implicit system : ActorSystem) extends Actor
    with ActorLogging {

    val db = Database.forConfig("plansDb")

    import com.server.provision.PlanDbActor._

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
            log.info(s"Finding Plan Details for id: #$id# with DB actor reference: ${self}")

            val action = plans.filter(_.id===id).map(u => (u.data_balance)).result.map(_.headOption.map{
                case data_balance => data_balance
            })

            val queryResult = db.run(action)

            sender()! ReceivePlanDetails(queryResult,id)

        case UpdateBalanceById(id:Int,balance:Int)=>
            log.info(s"Updating new balance:$balance for id: #$id# with DB actor reference: ${self}")
            val query = for { p <- plans if p.id === id } yield p.data_balance
            val updateAction = query.update(balance)


            db.run(updateAction)
    }
}
