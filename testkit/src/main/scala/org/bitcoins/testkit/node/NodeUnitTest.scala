package org.bitcoins.testkit.node

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import org.bitcoins.chain.api.ChainApi
import org.bitcoins.chain.config.ChainAppConfig
import org.bitcoins.core.config.NetworkParameters
import org.bitcoins.db.AppConfig
import org.bitcoins.node._
import org.bitcoins.node.config.NodeAppConfig
import org.bitcoins.node.models.Peer
import org.bitcoins.node.networking.peer.{
  PeerHandler,
  PeerMessageReceiver,
  PeerMessageReceiverState,
  PeerMessageSender
}
import org.bitcoins.rpc.client.common.BitcoindVersion.V18
import org.bitcoins.rpc.client.common.{BitcoindRpcClient, BitcoindVersion}
import org.bitcoins.server.BitcoinSAppConfig
import org.bitcoins.server.BitcoinSAppConfig._
import org.bitcoins.testkit.EmbeddedPg
import org.bitcoins.testkit.chain.ChainUnitTest
import org.bitcoins.testkit.fixtures.BitcoinSFixture
import org.bitcoins.testkit.keymanager.KeyManagerTestUtil
import org.bitcoins.testkit.node.fixture.{
  NeutrinoNodeConnectedWithBitcoind,
  NodeConnectedWithBitcoind,
  SpvNodeConnectedWithBitcoind
}
import org.bitcoins.testkit.rpc.BitcoindRpcTestUtil
import org.bitcoins.testkit.wallet.{BitcoinSWalletTest, WalletWithBitcoindRpc}
import org.scalatest.FutureOutcome

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait NodeUnitTest extends BitcoinSFixture with EmbeddedPg {

  override def beforeAll(): Unit = {
    AppConfig.throwIfDefaultDatadir(config.nodeConf)
    super[EmbeddedPg].beforeAll()
  }

  override def afterAll(): Unit = {
    super[EmbeddedPg].afterAll()
  }

  /** Wallet config with data directory set to user temp directory */
  implicit protected def config: BitcoinSAppConfig

  implicit protected lazy val chainConfig: ChainAppConfig = config.chainConf

  implicit protected lazy val nodeConfig: NodeAppConfig = config.nodeConf

  implicit override lazy val np: NetworkParameters = config.nodeConf.network

  lazy val startedBitcoindF = BitcoindRpcTestUtil.startedBitcoindRpcClient()

  lazy val bitcoindPeerF = startedBitcoindF.map(NodeTestUtil.getBitcoindPeer)

  def withSpvNodeConnectedToBitcoind(
      test: OneArgAsyncTest,
      versionOpt: Option[BitcoindVersion] = None)(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): FutureOutcome = {
    val nodeWithBitcoindBuilder: () => Future[SpvNodeConnectedWithBitcoind] = {
      () =>
        require(appConfig.isSPVEnabled && !appConfig.isNeutrinoEnabled)
        for {
          bitcoind <- BitcoinSFixture.createBitcoind(versionOpt)
          node <- NodeUnitTest.createSpvNode(bitcoind, NodeCallbacks.empty)(
            system,
            appConfig.chainConf,
            appConfig.nodeConf)
        } yield SpvNodeConnectedWithBitcoind(node, bitcoind)
    }

    makeDependentFixture(
      build = nodeWithBitcoindBuilder,
      destroy = NodeUnitTest.destroyNodeConnectedWithBitcoind(
        _: NodeConnectedWithBitcoind)(system, appConfig)
    )(test)
  }

  def withNeutrinoNodeConnectedToBitcoind(
      test: OneArgAsyncTest,
      versionOpt: Option[BitcoindVersion] = None)(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): FutureOutcome = {
    val nodeWithBitcoindBuilder: () => Future[
      NeutrinoNodeConnectedWithBitcoind] = { () =>
      require(appConfig.isNeutrinoEnabled && !appConfig.isSPVEnabled)
      for {
        bitcoind <- BitcoinSFixture.createBitcoind(versionOpt)
        node <- NodeUnitTest.createNeutrinoNode(bitcoind, NodeCallbacks.empty)(
          system,
          appConfig.chainConf,
          appConfig.nodeConf)
      } yield NeutrinoNodeConnectedWithBitcoind(node, bitcoind)
    }
    makeDependentFixture(
      build = nodeWithBitcoindBuilder,
      destroy = NodeUnitTest.destroyNodeConnectedWithBitcoind(
        _: NodeConnectedWithBitcoind)(system, appConfig)
    )(test)
  }

  def withSpvNodeFundedWalletBitcoind(
      test: OneArgAsyncTest,
      callbacks: NodeCallbacks,
      bip39PasswordOpt: Option[String])(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): FutureOutcome = {

    makeDependentFixture(
      build =
        () =>
          NodeUnitTest.createSpvNodeFundedWalletBitcoind(callbacks = callbacks,
                                                         bip39PasswordOpt =
                                                           bip39PasswordOpt,
                                                         versionOpt =
                                                           Option(V18))(
            system, // Force V18 because Spv is disabled on versions after
            appConfig),
      destroy = NodeUnitTest.destroyNodeFundedWalletBitcoind(
        _: NodeFundedWalletBitcoind)(system, appConfig)
    )(test)
  }

  def withNeutrinoNodeFundedWalletBitcoind(
      test: OneArgAsyncTest,
      callbacks: NodeCallbacks,
      bip39PasswordOpt: Option[String],
      versionOpt: Option[BitcoindVersion] = None)(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): FutureOutcome = {

    makeDependentFixture(
      build = () =>
        NodeUnitTest
          .createNeutrinoNodeFundedWalletBitcoind(
            callbacks,
            bip39PasswordOpt,
            versionOpt)(system, appConfig),
      destroy = NodeUnitTest.destroyNodeFundedWalletBitcoind(
        _: NodeFundedWalletBitcoind)(system, appConfig)
    )(test)
  }

  /** Helper method to generate blocks every interval */
  def genBlockInterval(bitcoind: BitcoindRpcClient)(
      implicit system: ActorSystem): Unit = {

    var counter = 0
    val desiredBlocks = 5
    val interval = 500.millis

    val genBlock = new Runnable {
      override def run(): Unit = {
        if (counter < desiredBlocks) {
          bitcoind.getNewAddress.flatMap(bitcoind.generateToAddress(1, _))
          counter = counter + 1
        }
      }
    }

    system.scheduler.scheduleAtFixedRate(2.second, interval)(genBlock)
    ()
  }

  def getBIP39PasswordOpt(): Option[String] =
    KeyManagerTestUtil.bip39PasswordOpt
}

object NodeUnitTest extends P2PLogger {

  def buildPeerMessageReceiver(chainApi: ChainApi, peer: Peer)(
      implicit appConfig: BitcoinSAppConfig,
      system: ActorSystem): Future[PeerMessageReceiver] = {
    val receiver =
      PeerMessageReceiver(state = PeerMessageReceiverState.fresh(),
                          chainApi = chainApi,
                          peer = peer,
                          callbacks = NodeCallbacks.empty)
    Future.successful(receiver)
  }

  def buildPeerHandler(peer: Peer)(
      implicit nodeAppConfig: NodeAppConfig,
      chainAppConfig: ChainAppConfig,
      system: ActorSystem): Future[PeerHandler] = {
    import system.dispatcher
    val chainApiF = ChainUnitTest.createChainHandler()
    val peerMsgReceiverF = chainApiF.flatMap { _ =>
      PeerMessageReceiver.preConnection(peer, NodeCallbacks.empty)
    }
    //the problem here is the 'self', this needs to be an ordinary peer message handler
    //that can handle the handshake
    val peerHandlerF = for {
      peerMsgReceiver <- peerMsgReceiverF
      client = NodeTestUtil.client(peer, peerMsgReceiver)
      peerMsgSender = PeerMessageSender(client)
    } yield PeerHandler(client, peerMsgSender)

    peerHandlerF

  }

  def destroyNode(node: Node)(
      implicit config: BitcoinSAppConfig,
      ec: ExecutionContext): Future[Unit] = {
    node
      .stop()
      .flatMap(_ => ChainUnitTest.destroyAllTables())
  }

  def destroyNodeConnectedWithBitcoind(
      nodeConnectedWithBitcoind: NodeConnectedWithBitcoind)(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): Future[Unit] = {
    logger(appConfig.nodeConf)
      .debug(s"Beggining tear down of node connected with bitcoind")
    import system.dispatcher
    val node = nodeConnectedWithBitcoind.node
    val bitcoind = nodeConnectedWithBitcoind.bitcoind
    val resultF = for {
      _ <- destroyNode(node)
      _ <- ChainUnitTest.destroyBitcoind(bitcoind)
    } yield {
      logger(appConfig.nodeConf)
        .debug(s"Done with teardown of node connected with bitcoind!")
      ()
    }

    resultF
  }

  /** Creates a spv node, a funded bitcoin-s wallet, all of which are connected to bitcoind */
  def createSpvNodeFundedWalletBitcoind(
      callbacks: NodeCallbacks,
      bip39PasswordOpt: Option[String],
      versionOpt: Option[BitcoindVersion] = None)(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): Future[SpvNodeFundedWalletBitcoind] = {
    import system.dispatcher
    require(appConfig.isSPVEnabled && !appConfig.isNeutrinoEnabled)
    for {
      bitcoind <- BitcoinSFixture.createBitcoindWithFunds(versionOpt)
      node <- createSpvNode(bitcoind, callbacks)
      fundedWallet <- BitcoinSWalletTest.fundedWalletAndBitcoind(
        bitcoind,
        node,
        node,
        bip39PasswordOpt)
    } yield {
      SpvNodeFundedWalletBitcoind(node = node,
                                  wallet = fundedWallet.wallet,
                                  bitcoindRpc = fundedWallet.bitcoind,
                                  bip39PasswordOpt)
    }
  }

  /** Creates a neutrino node, a funded bitcoin-s wallet, all of which are connected to bitcoind */
  def createNeutrinoNodeFundedWalletBitcoind(
      callbacks: NodeCallbacks,
      bip39PasswordOpt: Option[String],
      versionOpt: Option[BitcoindVersion])(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): Future[NeutrinoNodeFundedWalletBitcoind] = {
    import system.dispatcher
    require(appConfig.isNeutrinoEnabled && !appConfig.isSPVEnabled)
    for {
      bitcoind <- BitcoinSFixture.createBitcoindWithFunds(versionOpt)
      node <- createNeutrinoNode(bitcoind, callbacks)
      fundedWallet <- BitcoinSWalletTest.fundedWalletAndBitcoind(
        bitcoindRpcClient = bitcoind,
        nodeApi = node,
        chainQueryApi = node,
        bip39PasswordOpt = bip39PasswordOpt)
    } yield {
      NeutrinoNodeFundedWalletBitcoind(node = node,
                                       wallet = fundedWallet.wallet,
                                       bitcoindRpc = fundedWallet.bitcoind,
                                       bip39PasswordOpt = bip39PasswordOpt)
    }
  }

  def destroyNodeFundedWalletBitcoind(
      fundedWalletBitcoind: NodeFundedWalletBitcoind)(
      implicit system: ActorSystem,
      appConfig: BitcoinSAppConfig): Future[Unit] = {
    import system.dispatcher
    val walletWithBitcoind = {
      WalletWithBitcoindRpc(fundedWalletBitcoind.wallet,
                            fundedWalletBitcoind.bitcoindRpc)
    }

    //these need to be done in order, as the spv node needs to be
    //stopped before the bitcoind node is stopped
    val destroyedF = for {
      _ <- destroyNode(fundedWalletBitcoind.node)
      _ <- BitcoinSWalletTest.destroyWalletWithBitcoind(walletWithBitcoind)
    } yield ()

    destroyedF

  }

  def buildPeerMessageReceiver(chainApi: ChainApi, peer: Peer)(
      implicit nodeAppConfig: NodeAppConfig,
      chainAppConfig: ChainAppConfig,
      system: ActorSystem): Future[PeerMessageReceiver] = {
    val receiver =
      PeerMessageReceiver(state = PeerMessageReceiverState.fresh(),
                          chainApi = chainApi,
                          peer = peer,
                          callbacks = NodeCallbacks.empty)
    Future.successful(receiver)
  }

  def peerSocketAddress(
      bitcoindRpcClient: BitcoindRpcClient): InetSocketAddress = {
    NodeTestUtil.getBitcoindSocketAddress(bitcoindRpcClient)
  }

  def createPeer(bitcoind: BitcoindRpcClient): Peer = {
    val socket = peerSocketAddress(bitcoind)
    Peer(id = None, socket = socket)
  }

  /** Creates a spv node peered with the given bitcoind client, this method
    * also calls [[org.bitcoins.node.Node.start() start]] to start the node */
  def createSpvNode(bitcoind: BitcoindRpcClient, callbacks: NodeCallbacks)(
      implicit system: ActorSystem,
      chainAppConfig: ChainAppConfig,
      nodeAppConfig: NodeAppConfig): Future[SpvNode] = {
    import system.dispatcher
    val checkConfigF = Future {
      assert(nodeAppConfig.isSPVEnabled)
      assert(!nodeAppConfig.isNeutrinoEnabled)
    }
    val chainApiF = for {
      _ <- checkConfigF
      chainHandler <- ChainUnitTest.createChainHandler()
    } yield chainHandler
    val peer = createPeer(bitcoind)
    val nodeF = for {
      _ <- chainApiF
    } yield {
      SpvNode(
        nodePeer = peer,
        nodeConfig = nodeAppConfig,
        chainConfig = chainAppConfig,
        actorSystem = system
      ).setBloomFilter(NodeTestUtil.emptyBloomFilter)
    }

    nodeF.flatMap(_.addCallbacks(callbacks).start()).flatMap(_ => nodeF)
  }

  /** Creates a Neutrino node peered with the given bitcoind client, this method
    * also calls [[org.bitcoins.node.Node.start() start]] to start the node */
  def createNeutrinoNode(bitcoind: BitcoindRpcClient, callbacks: NodeCallbacks)(
      implicit system: ActorSystem,
      chainAppConfig: ChainAppConfig,
      nodeAppConfig: NodeAppConfig): Future[NeutrinoNode] = {
    import system.dispatcher
    val checkConfigF = Future {
      assert(!nodeAppConfig.isSPVEnabled)
      assert(nodeAppConfig.isNeutrinoEnabled)
    }
    val chainApiF = for {
      _ <- checkConfigF
      chainHandler <- ChainUnitTest.createChainHandler()
    } yield chainHandler
    val peer = createPeer(bitcoind)
    val nodeF = for {
      _ <- chainApiF
    } yield {
      NeutrinoNode(nodePeer = peer,
                   nodeConfig = nodeAppConfig,
                   chainConfig = chainAppConfig,
                   actorSystem = system)
    }

    nodeF.flatMap(_.addCallbacks(callbacks).start()).flatMap(_ => nodeF)
  }

}