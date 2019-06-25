
package com.server.provision

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
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
class MediatorActor(implicit materializer: ActorMaterializer, system: ActorSystem) extends Actor {
  import MediatorActor._

  implicit val ec = system.dispatcher
  val planDataActor: ActorRef = context.actorOf(PlanDbActor.props, "planDataActor")
  var balanceMeterActor:ActorRef=null

  override def preStart(): Unit = {
    println("MediatorActor started")

  }
  override def postStop(): Unit = {
    println("MediatorActor stopped")
  }

  def show(x: Option[Int]):Int = x match {
    case Some(s) => s
    case None => 0
  }
  override def receive: Receive = {
    case InitiateMeter(id)=>
          println("INitiating meter inside mediator")
      planDataActor ! FindPlanById(id)
    case EndCallMediator=>
      balanceMeterActor ! EndCallMeter
    case UpdateBalance(id,balance)=>
      //      balanceMeterActor!PoisonPill
      planDataActor ! UpdateBalanceById(id,balance)
      context stop self
    case ReplyToMeter(f,id)=>
      println("called ReplyToMeter")

      f.map {
        case x:Some[Int] =>

          val balance:Int=show(x)
          balanceMeterActor = context.actorOf(MeteringActor.props(id,balance), name = s"balanceMeterActor-$id")
        //          balanceMeterActor ! DecreaseBalance

        case None => println("Record Not Found")//Success with None

      }

    //      f.onComplete {
    //                      case s => println(s"Result: $s")
    //                      val balanceMeterActor = context.actorOf(BalanceMeterActor.props(4,3000), name = "balanceMeterActor")
    //                      balanceMeterActor ! DecreaseBalance
    //                    }


  }

}
