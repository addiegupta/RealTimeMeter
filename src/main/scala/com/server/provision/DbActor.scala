
package com.server.provision

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.server.provision.MediatorActor._
import slick.jdbc.PostgresProfile.api._

/**
  * Companion object for the DbActor class
  * Contains the messages that this Actor receives and the props method that is used to construct a
  * DbActor instance, passing in the implicit ActorSystem
  */
object DbActor{
    def props(implicit system : ActorSystem) =
        Props(classOf[DbActor],system)

    case class FindPlanById(id:Int)

    case class UpdateBalanceById(id:Int,data_balance:Int)
}

/**
  * Interacts with the database
  * Created by a supervising  DB Pooling Actor in the WebServer
*/
class DbActor(implicit system : ActorSystem) extends Actor
    with ActorLogging {
    import com.server.provision.DbActor._

    // Reference to database, accessed using Slick library
    val db = Database.forConfig("plansDb")

    implicit val ec = system.dispatcher

    // Plans class to extract data from database
    class Plans(tag: Tag) extends Table[(Int,String, String, Int)](tag, "plans") {
        def id = column[Int]("id", O.PrimaryKey,O.AutoInc) // This is the primary key column
        def number = column[String]("number")
        def name = column[String]("name")
        def data_balance = column[Int]("data_balance")
        // Every table needs a * projection with the same type as the table's type parameter
        def * = (id,number,name,data_balance)
    }

    // Query to fetch all data from database
    val plans = TableQuery[Plans]

    override def receive: Receive = {

        // Finds the data plan of user, sent by MediatorActor
        case FindPlanById(id:Int)=>

            log.info(s"Finding Plan Details for id: #$id# with DB actor reference: ${self}")

            // Refine the query to fetch only the data_balance value for given id
            val action = plans.filter(_.id===id).map(u => (u.data_balance)).result.map(_.headOption.map{
                case data_balance => data_balance
            })

            // Run the query, a future is returned which will contain the balance value at a later point in time
            val queryResult = db.run(action)

            // Send the future containing balance back to original sender of message i.e. Mediator
            sender()! ReceivePlanDetails(queryResult,id)

        // Update balance of user in the database
        case UpdateBalanceById(id:Int,balance:Int)=>

            log.info(s"Updating new balance:$balance for id: #$id# with DB actor reference: ${self}")

            // Update the database for given id and balance
            val query = for { p <- plans if p.id === id } yield p.data_balance
            val updateAction = query.update(balance)
            db.run(updateAction)
    }
}
