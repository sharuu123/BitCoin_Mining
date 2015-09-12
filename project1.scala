import java.security.MessageDigest
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.PoisonPill
import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.remote.RemoteActorRefProvider

object project1 {
	def main(args: Array[String]){
		println(sha256("sarojini!!!!!HAVHgogfflalfbalffFIFfGAIS"))
		var timer: Boolean = true
		var actorList: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]

		case class SearchBitcoins()
		case class Result()
		case class StartWork(startrange: Int, endrange:Int)
		case class ping()
		case class WorkDone(list:StringBuilder)
		case class Consolidate(list: StringBuilder)
		case class RemoteWorkDone(list: StringBuilder)
		case class RemoteSearchBitcoins()
		case class PrintBitcoins(masterList: StringBuilder)
		case class ShutDown(masterList: StringBuilder)
		
		
		// Four type of actors - Master, RemoteMaster, Worker, Listener
		class Master(nrOfWorkers: Int, targetZeroes: Int, listener: ActorRef)
			extends Actor{

				var masterList = new StringBuilder("": String)
				var inc: Int = _
				val starttime: Long = System.currentTimeMillis
				val worksize: Int = 1000000
			def receive = {
				case SearchBitcoins() => 
					for(i <- 0 until nrOfWorkers) {
						val actor = context.actorOf(Props(new Worker(targetZeroes))) ! StartWork(inc*worksize,(inc+1)*worksize)
						actorList += actor
						inc = inc+1
					}

				case WorkDone(list: StringBuilder) =>
					if(timer){
						masterList.append(list)
						inc = inc+1
						sender ! StartWork((inc-1)*worksize,(inc)*worksize)
					}

				case Consolidate(list: StringBuilder) =>
					if(timer) {
						masterList.append(list)
					}
					
				case "STOP" =>
					timer = false
					for(actor <- actorList){
						actor ! poisonPill
					}
					listener ! PrintBitcoins(masterList)
					
			}
		}

		class RemoteMaster(nrOfWorkers: Int, ipAddress: String, listener: ActorRef) 
			extends Actor{

			val remote = context.actorFor("akka.tcp://RemoteMasterSystem@" + ipAdress + ":2250/user/Master")
			def receive = {
				case RemoteSearchBitcoins() => 
					for(i <- 0 until nrOfWorkers) {
						val actor = context.actorOf(Props(new Worker(targetZeroes))) ! StartWork(inc*worksize,(inc+1)*worksize)
						actorList += actor
						inc = inc+1
					}
				case RemoteWorkDone(list: StringBuilder) =>
					if(timer){
						masterList.append(list)
						inc = inc+1
						sender ! StartWork((inc-1)*worksize,(inc)*worksize)
					}

				case "REMOTE_STOP" =>
					timer = false
					for(actor <- actorList){
						actor ! poisonPill
					}
					listener ! ShutDown(masterList,ipAddress)
				
			}

		}

		class Worker(targetzeroes:Int) extends Actor{
			def receive = {
				case StartWork(startrange: Int, endrange:Int) =>
					var list = new StringBuilder("": String)
					for (i <- startrange until endrange){
						var str = "sdarsha".concat(Integer.toString(i,36))
						var crypted = sha256(str)
						if(hasZeroes(crypted,targetzeroes)){
							list.append(cryped)
						}
					}
					sender ! WorkDone(list)

			}
		}

		def sha256(s: String): String = {
			val md = MessageDigest.getInstance("SHA-256")
			val digest: Array[Byte] = md.digest(s.getBytes)
			var sb: StringBuffer = new StringBuffer
			digest.foreach { digest =>
				var hex = Integer.toHexString(digest & 0xff)
				if (hex.length == 1) sb.append('0') 
				sb.append(hex)
			}
			sb.toString()
		}

		def hasZeroes(s: String, targetzeroes:Int): Boolean = {
			var count: Int = targetzeroes
			var found : Boolean= true;
			while(count>0 && found){
				if(s.charAt(targetzeroes-count) == '0'){
					count--;
				} else{
					found = false
				}
			}
			found
		}

		class Listener extends Actor{
			def receive = {
				case PrintBitcoins(masterList: StringBuilder) => 
					sender ! PoisonPill
					println(masterList)
					context.system.shutdown()

				case ShutDown(masterList: StringBuilder, ipAddress: String) =>
					val masterActor = system.actorSelection("akka.tcp://RemoteMasterSystem@" + ipAddress + ":2552/user/master")
					masterActor ! Consolidate(masterlist)
					sender ! PoisonPill
					context.system.shutdown()
			}
		}

		 val MasterConfig = ConfigFactory.parseString("""akka{
				actor{
					provider = "akka.remote.RemoteActorRefProvider"
				}
				remote{
					enabled-transports = ["akka.remote.netty.tcp"]
					netty.tcp {
						hostname = "127.0.0.1"
						port = 2552
					}
				}
			}""")
		val RemoteMasterConfig = ConfigFactory.parseString("""akka{
				actor{
					provider = "akka.remote.RemoteActorRefProvider"
				}
				remote{
					enabled-transports = ["akka.remote.netty.tcp"]
					netty.tcp{
						port = 0
					}
				}
			}""")

		if(args(0).contains('.')) {
			val nrOfWorkers: Int = Runtime.getRuntime().availableProcessors()
			val system = ActorSystem("RemoteMasterSystem", ConfigFactory.load(RemoteMasterConfig))
			val listener = system.actorOf(Props[Listener], name = "listener")
			val remotemaster = system.actorOf(Props(new RemoteMaster(nrOfWorkers,args(0), listener)), name = "remotemaster")
			
			import system.dispatcher
			system.scheduler.scheduleOnce(300000 milliseconds, remotemaster, "REMOTE_STOP")
			remotemaster ! RemoteSearchBitcoins()
		} else {
			val nrOfWorkers: Int = Runtime.getRuntime().availableProcessors()
			val system = ActorSystem("MasterSystem", ConfigFactory.load(MasterConfig))
			val listener = system.actorOf(Props[Listener], name = "listener")
			val master = system.actorOf(Props(new Master(nrOfWorkers,args(0).toInt, listener)), name = "master")
			// val master = system.actorOf(Props(new Master(4,4, listener), name = "master"))
			//Scheduler to give STOP command after 2 min
			import system.dispatcher
			system.scheduler.scheduleOnce(500000 milliseconds, master, "STOP")
			master ! SearchBitcoins()
		}
	}

	

	
}