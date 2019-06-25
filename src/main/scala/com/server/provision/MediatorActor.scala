
package com.server.provision

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.server.provision.PlanDbActor.{FindPlanById, UpdateBalanceById}
import com.server.provision.MeteringActor.EndCallMeter

import scala.concurrent.Future

object MediatorActor{
  def props(implicit materializer: ActorMaterializer,system : ActorSystem) =
    Props(classOf[MediatorActor],materializer,system)

  case object EndCallMediator
  case class InitiateMeter(id:Int)
  case class UpdateBalance(id:Int,balance:Int)
  case class ReplyToMeter(f:Future[Option[Int]],id:Int)

}
class MediatorActor(implicit materializer: ActorMaterializer, system: ActorSystem) extends Actor
with ActorLogging{
  import MediatorActor._

  implicit val ec = system.dispatcher
  val planDataActor: ActorRef = context.actorOf(PlanDbActor.props, "planDataActor")
  var balanceMeterActor:ActorRef=null

  override def preStart(): Unit = {
    log.info("New MediatorActor started")

  }
  override def postStop(): Unit = {
    log.info("MediatorActor stopped")
  }

  def show(x: Option[Int]):Int = x match {
    case Some(s) => s
    case None => 0
  }
  override def receive: Receive = {
    case InitiateMeter(id)=>
      log.info(s"Initiating meter for id: $id inside mediator")
      planDataActor ! FindPlanById(id)
    case EndCallMediator=>
      balanceMeterActor ! EndCallMeter
    case UpdateBalance(id,balance)=>
      //      balanceMeterActor!PoisonPill
      planDataActor ! UpdateBalanceById(id,balance)
      context stop self
    case ReplyToMeter(f,id)=>
      log.info(s"ReplyToMeter called for id: $id ")

      f.map {
        case x:Some[Int] =>

          val balance:Int=show(x)
          balanceMeterActor = context.actorOf(MeteringActor.props(id,balance), name = s"balanceMeterActor-$id")
        //          balanceMeterActor ! DecreaseBalance

        case None =>log.info(s"Record Not Found for $id")//Success with None

      }

    //      f.onComplete {
    //                      case s => println(s"Result: $s")
    //                      val balanceMeterActor = context.actorOf(BalanceMeterActor.props(4,3000), name = "balanceMeterActor")
    //                      balanceMeterActor ! DecreaseBalance
    //                    }


  }

}
