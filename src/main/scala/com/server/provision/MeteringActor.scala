package com.server.provision


import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.ActorMaterializer
import com.server.provision.MediatorActor.UpdateBalance
import com.server.provision.MeteringActor.EndCallMeter

import scala.concurrent.duration._

object MeteringActor
{
    def props(id: Int,balance:Int)(implicit materializer: ActorMaterializer, system: ActorSystem)  =
        Props(classOf[MeteringActor],id,balance,materializer,system)
    case object EndCallMeter

}

class MeteringActor(id: Int, var balance:Int) (implicit materializer: ActorMaterializer, system: ActorSystem) extends Actor{
    var cancellable:Cancellable = null
    implicit val ec = system.dispatcher
    override def preStart(): Unit = {
        println("Balance meter started with balance")
        println(balance," id:",id)

        cancellable = context.system.scheduler.schedule(0 seconds, 1 seconds) {

            balance-=1
            println("tick:"+balance)
            //      if(balance==1375){
            //        cancellable.cancel()
            //        context.parent!UpdateBalance(id,balance)
            //        context stop self
            //      }

        }
        //This cancels further Ticks to be sent



    }
    override def receive: Receive = {
        case EndCallMeter=>
            println("Call Ended")
            cancellable.cancel()
            sender()!UpdateBalance(id,balance)
            context stop self
        //      val planDataActor: ActorRef = context.actorOf(PlanDataActor.props, "planDataActor")
        //
        //
        //
        //      planDataActor ! UpdateBalanceById(id,balance-10)

    }
    override def postStop(): Unit = {
        println("Balance meter stopped")

    }
}
