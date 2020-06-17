package org.bitcoins.cli

import org.bitcoins.cli.CliCommand._
import org.bitcoins.cli.CliReaders._
import org.bitcoins.commons.jsonmodels.wallet.CoinSelectionAlgo
import org.bitcoins.commons.serializers.Picklers._
import org.bitcoins.core.config.NetworkParameters
import org.bitcoins.core.currency._
import org.bitcoins.core.protocol.transaction.{
  EmptyTransaction,
  Transaction,
  TransactionOutPoint
}
import org.bitcoins.core.protocol.{BitcoinAddress, BlockStamp}
import org.bitcoins.core.psbt.PSBT
import org.bitcoins.core.wallet.fee.SatoshisPerVirtualByte
import scopt.OParser
import ujson.{Num, Str}
import upickle.{default => up}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object ConsoleCli {

  def parser: OParser[Unit, Config] = {
    val builder = OParser.builder[Config]

    import builder._
    OParser.sequence(
      programName("bitcoin-s-cli"),
      opt[NetworkParameters]('n', "network")
        .action((np, conf) => conf.copy(network = Some(np)))
        .text("Select the active network."),
      opt[Unit]("debug")
        .action((_, conf) => conf.copy(debug = true))
        .text("Print debugging information"),
      opt[Int]("rpcport")
        .action((port, conf) => conf.copy(rpcPort = port))
        .text(s"The port to send our rpc request to on the server"),
      help('h', "help").text("Display this help message and exit"),
      note(sys.props("line.separator") + "Commands:"),
      note(sys.props("line.separator") + "===Blockchain ==="),
      cmd("getblockcount")
        .action((_, conf) => conf.copy(command = GetBlockCount))
        .text(s"Get the block height"),
      cmd("getfiltercount")
        .action((_, conf) => conf.copy(command = GetFilterCount))
        .text(s"Get the number of filters"),
      cmd("getfilterheadercount")
        .action((_, conf) => conf.copy(command = GetFilterHeaderCount))
        .text(s"Get the number of filter headers"),
      cmd("getbestblockhash")
        .action((_, conf) => conf.copy(command = GetBestBlockHash))
        .text(s"Get the best block hash"),
      note(sys.props("line.separator") + "=== Wallet ==="),
      cmd("rescan")
        .action(
          (_, conf) =>
            conf.copy(
              command = Rescan(addressBatchSize = Option.empty,
                               startBlock = Option.empty,
                               endBlock = Option.empty,
                               force = false,
                               ignoreCreationTime = false)))
        .text(s"Rescan for wallet UTXOs")
        .children(
          opt[Unit]("force")
            .text("Clears existing wallet records. Warning! Use with caution!")
            .optional()
            .action((_, conf) =>
              conf.copy(command = conf.command match {
                case rescan: Rescan =>
                  rescan.copy(force = true)
                case other => other
              })),
          opt[Int]("batch-size")
            .text("Number of filters that can be matched in one batch")
            .optional()
            .action((batchSize, conf) =>
              conf.copy(command = conf.command match {
                case rescan: Rescan =>
                  rescan.copy(addressBatchSize = Option(batchSize))
                case other => other
              })),
          opt[BlockStamp]("start")
            .text("Start height")
            .optional()
            .action((start, conf) =>
              conf.copy(command = conf.command match {
                case rescan: Rescan =>
                  // Need to ignoreCreationTime so we try to call
                  // rescan with rescanNeutrinoWallet with a block
                  // and a creation time
                  rescan.copy(startBlock = Option(start),
                              ignoreCreationTime = true)
                case other => other
              })),
          opt[BlockStamp]("end")
            .text("End height")
            .optional()
            .action((end, conf) =>
              conf.copy(command = conf.command match {
                case rescan: Rescan =>
                  rescan.copy(endBlock = Option(end))
                case other => other
              })),
          opt[Unit]("ignorecreationtime")
            .text("Ignores the wallet creation date and will instead do a full rescan")
            .optional()
            .action((_, conf) =>
              conf.copy(command = conf.command match {
                case rescan: Rescan =>
                  rescan.copy(ignoreCreationTime = true)
                case other => other
              }))
        ),
      cmd("isempty")
        .action((_, conf) => conf.copy(command = IsEmpty))
        .text("Checks if the wallet contains any data"),
      cmd("getbalance")
        .action((_, conf) => conf.copy(command = GetBalance(false)))
        .text("Get the wallet balance")
        .children(
          opt[Unit]("sats")
            .optional()
            .text("Display balance in satoshis")
            .action((_, conf) =>
              conf.copy(command = conf.command match {
                case getBalance: GetBalance =>
                  getBalance.copy(isSats = true)
                case other => other
              }))
        ),
      cmd("getconfirmedbalance")
        .action((_, conf) => conf.copy(command = GetConfirmedBalance(false)))
        .text("Get the wallet balance of confirmed utxos")
        .children(
          opt[Unit]("sats")
            .optional()
            .text("Display balance in satoshis")
            .action((_, conf) =>
              conf.copy(command = conf.command match {
                case getBalance: GetConfirmedBalance =>
                  getBalance.copy(isSats = true)
                case other => other
              }))
        ),
      cmd("getunconfirmedbalance")
        .action((_, conf) => conf.copy(command = GetUnconfirmedBalance(false)))
        .text("Get the wallet balance of unconfirmed utxos")
        .children(
          opt[Unit]("sats")
            .optional()
            .text("Display balance in satoshis")
            .action((_, conf) =>
              conf.copy(command = conf.command match {
                case getBalance: GetUnconfirmedBalance =>
                  getBalance.copy(isSats = true)
                case other => other
              }))
        ),
      cmd("getutxos")
        .action((_, conf) => conf.copy(command = GetUtxos))
        .text("Returns list of all wallet utxos"),
      cmd("getaddresses")
        .action((_, conf) => conf.copy(command = GetAddresses))
        .text("Returns list of all wallet addresses currently being watched"),
      cmd("getspentaddresses")
        .action((_, conf) => conf.copy(command = GetSpentAddresses))
        .text(
          "Returns list of all wallet addresses that have received funds and been spent"),
      cmd("getfundedaddresses")
        .action((_, conf) => conf.copy(command = GetFundedAddresses))
        .text("Returns list of all wallet addresses that are holding funds"),
      cmd("getunusedaddresses")
        .action((_, conf) => conf.copy(command = GetUnusedAddresses))
        .text("Returns list of all wallet addresses that have not been used"),
      cmd("getaccounts")
        .action((_, conf) => conf.copy(command = GetAccounts))
        .text("Returns list of all wallet accounts"),
      cmd("createnewaccount")
        .action((_, conf) => conf.copy(command = CreateNewAccount))
        .text("Creates a new wallet account"),
      cmd("getaddressinfo")
        .action((_, conf) => conf.copy(command = GetAddressInfo(null)))
        .text("Returns list of all wallet accounts")
        .children(
          arg[BitcoinAddress]("address")
            .text("Address to get information about")
            .required()
            .action((addr, conf) =>
              conf.copy(command = conf.command match {
                case getAddressInfo: GetAddressInfo =>
                  getAddressInfo.copy(address = addr)
                case other => other
              }))
        ),
      cmd("getnewaddress")
        .action((_, conf) => conf.copy(command = GetNewAddress))
        .text("Get a new address"),
      cmd("sendtoaddress")
        .action(
          // TODO how to handle null here?
          (_, conf) => conf.copy(command = SendToAddress(null, 0.bitcoin, None)))
        .text("Send money to the given address")
        .children(
          arg[BitcoinAddress]("address")
            .text("Address to send to")
            .required()
            .action((addr, conf) =>
              conf.copy(command = conf.command match {
                case send: SendToAddress =>
                  send.copy(destination = addr)
                case other => other
              })),
          arg[Bitcoins]("amount")
            .text("amount to send in BTC")
            .required()
            .action((btc, conf) =>
              conf.copy(command = conf.command match {
                case send: SendToAddress =>
                  send.copy(amount = btc)
                case other => other
              })),
          opt[SatoshisPerVirtualByte]("feerate")
            .text("Fee rate in sats per virtual byte")
            .optional()
            .action((feeRate, conf) =>
              conf.copy(command = conf.command match {
                case send: SendToAddress =>
                  send.copy(satoshisPerVirtualByte = Some(feeRate))
                case other => other
              }))
        ),
      cmd("sendfromoutpoints")
        .action((_, conf) =>
          conf.copy(
            command = SendFromOutPoints(Vector.empty, null, 0.bitcoin, None)))
        .text("Send money to the given address")
        .children(
          arg[Seq[TransactionOutPoint]]("outpoints")
            .text("Out Points to send from")
            .required()
            .action((outPoints, conf) =>
              conf.copy(command = conf.command match {
                case send: SendFromOutPoints =>
                  send.copy(outPoints = outPoints.toVector)
                case other => other
              })),
          arg[BitcoinAddress]("address")
            .text("Address to send to")
            .required()
            .action((addr, conf) =>
              conf.copy(command = conf.command match {
                case send: SendFromOutPoints =>
                  send.copy(destination = addr)
                case other => other
              })),
          arg[Bitcoins]("amount")
            .text("amount to send in BTC")
            .required()
            .action((btc, conf) =>
              conf.copy(command = conf.command match {
                case send: SendFromOutPoints =>
                  send.copy(amount = btc)
                case other => other
              })),
          opt[SatoshisPerVirtualByte]("feerate")
            .text("Fee rate in sats per virtual byte")
            .optional()
            .action((feeRate, conf) =>
              conf.copy(command = conf.command match {
                case send: SendFromOutPoints =>
                  send.copy(feeRateOpt = Some(feeRate))
                case other => other
              }))
        ),
      cmd("sendwithalgo")
        .action((_, conf) =>
          conf.copy(command = SendWithAlgo(null, 0.bitcoin, None, null)))
        .text(
          "Send money to the given address using a specific coin selection algo")
        .children(
          arg[BitcoinAddress]("address")
            .text("Address to send to")
            .required()
            .action((addr, conf) =>
              conf.copy(command = conf.command match {
                case send: SendWithAlgo =>
                  send.copy(destination = addr)
                case other => other
              })),
          arg[Bitcoins]("amount")
            .text("amount to send in BTC")
            .required()
            .action((btc, conf) =>
              conf.copy(command = conf.command match {
                case send: SendWithAlgo =>
                  send.copy(amount = btc)
                case other => other
              })),
          arg[CoinSelectionAlgo]("algo")
            .text("Coin selection algo")
            .optional()
            .action((algo, conf) =>
              conf.copy(command = conf.command match {
                case send: SendWithAlgo =>
                  send.copy(algo = algo)
                case other => other
              })),
          opt[SatoshisPerVirtualByte]("feerate")
            .text("Fee rate in sats per virtual byte")
            .optional()
            .action((feeRate, conf) =>
              conf.copy(command = conf.command match {
                case send: SendWithAlgo =>
                  send.copy(feeRateOpt = Some(feeRate))
                case other => other
              }))
        ),
      cmd("opreturncommit")
        .action((_, conf) =>
          conf.copy(command = OpReturnCommit("", hashMessage = false, None)))
        .text("Creates OP_RETURN commitment transaction")
        .children(
          arg[String]("message")
            .text("message to put into OP_RETURN commitment")
            .required()
            .action((message, conf) =>
              conf.copy(command = conf.command match {
                case opReturnCommit: OpReturnCommit =>
                  opReturnCommit.copy(message = message)
                case other => other
              })),
          opt[Unit]("hashMessage")
            .text("should the message be hashed before commitment")
            .optional()
            .action((_, conf) =>
              conf.copy(command = conf.command match {
                case opReturnCommit: OpReturnCommit =>
                  opReturnCommit.copy(hashMessage = true)
                case other => other
              })),
          opt[SatoshisPerVirtualByte]("feerate")
            .text("Fee rate in sats per virtual byte")
            .optional()
            .action((feeRate, conf) =>
              conf.copy(command = conf.command match {
                case opReturnCommit: OpReturnCommit =>
                  opReturnCommit.copy(feeRateOpt = Some(feeRate))
                case other => other
              }))
        ),
      note(sys.props("line.separator") + "=== Network ==="),
      cmd("getpeers")
        .action((_, conf) => conf.copy(command = GetPeers))
        .text(s"List the connected peers"),
      cmd("stop")
        .action((_, conf) => conf.copy(command = Stop))
        .text("Request a graceful shutdown of Bitcoin-S"),
      cmd("sendrawtransaction")
        .action((_, conf) =>
          conf.copy(command = SendRawTransaction(EmptyTransaction)))
        .text("Broadcasts the raw transaction")
        .children(
          arg[Transaction]("tx")
            .text("Transaction serialized in hex")
            .required()
            .action((tx, conf) =>
              conf.copy(command = conf.command match {
                case sendRawTransaction: SendRawTransaction =>
                  sendRawTransaction.copy(tx = tx)
                case other => other
              }))
        ),
      note(sys.props("line.separator") + "=== PSBT ==="),
      cmd("combinepsbts")
        .action((_, conf) => conf.copy(command = CombinePSBTs(Seq.empty)))
        .text("Combines all the given PSBTs")
        .children(
          arg[Seq[PSBT]]("psbts")
            .text("PSBTs serialized in hex or base64 format")
            .required()
            .action((seq, conf) =>
              conf.copy(command = conf.command match {
                case combinePSBTs: CombinePSBTs =>
                  combinePSBTs.copy(psbts = seq)
                case other => other
              }))
        ),
      cmd("joinpsbts")
        .action((_, conf) => conf.copy(command = JoinPSBTs(Seq.empty)))
        .text("Combines all the given PSBTs")
        .children(
          arg[Seq[PSBT]]("psbts")
            .text("PSBTs serialized in hex or base64 format")
            .required()
            .action((seq, conf) =>
              conf.copy(command = conf.command match {
                case joinPSBTs: JoinPSBTs =>
                  joinPSBTs.copy(psbts = seq)
                case other => other
              }))
        ),
      cmd("finalizepsbt")
        .action((_, conf) => conf.copy(command = FinalizePSBT(PSBT.empty)))
        .text("Finalizes the given PSBT if it can")
        .children(
          arg[PSBT]("psbt")
            .text("PSBT serialized in hex or base64 format")
            .required()
            .action((psbt, conf) =>
              conf.copy(command = conf.command match {
                case finalizePSBT: FinalizePSBT =>
                  finalizePSBT.copy(psbt = psbt)
                case other => other
              }))
        ),
      cmd("extractfrompsbt")
        .action((_, conf) => conf.copy(command = ExtractFromPSBT(PSBT.empty)))
        .text("Extracts a transaction from the given PSBT if it can")
        .children(
          arg[PSBT]("psbt")
            .text("PSBT serialized in hex or base64 format")
            .required()
            .action((psbt, conf) =>
              conf.copy(command = conf.command match {
                case extractFromPSBT: ExtractFromPSBT =>
                  extractFromPSBT.copy(psbt = psbt)
                case other => other
              }))
        ),
      cmd("converttopsbt")
        .action((_, conf) =>
          conf.copy(command = ConvertToPSBT(EmptyTransaction)))
        .text("Creates an empty psbt from the given transaction")
        .children(
          arg[Transaction]("unsignedTx")
            .text("serialized unsigned transaction in hex")
            .required()
            .action((tx, conf) =>
              conf.copy(command = conf.command match {
                case convertToPSBT: ConvertToPSBT =>
                  convertToPSBT.copy(transaction = tx)
                case other => other
              }))
        ),
      checkConfig {
        case Config(NoCommand, _, _, _) =>
          failure("You need to provide a command!")
        case _ => success
      }
    )
  }

  def exec(args: String*): Try[String] = {
    val config = OParser.parse(parser, args.toVector, Config()) match {
      case None       => sys.exit(1)
      case Some(conf) => conf
    }

    exec(config.command, config)
  }

  def exec(command: CliCommand, config: Config): Try[String] = {
    import System.err.{println => printerr}

    /** Prints the given message to stderr if debug is set */
    def debug(message: Any): Unit = {
      if (config.debug) {
        printerr(s"DEBUG: $message")
      }
    }

    /** Prints the given message to stderr and exist */
    def error[T](message: String): Failure[T] = {
      Failure(new RuntimeException(message))
    }

    val requestParam: RequestParam = command match {
      case GetUtxos =>
        RequestParam("getutxos")
      case GetAddresses =>
        RequestParam("getaddresses")
      case GetSpentAddresses =>
        RequestParam("getspentaddresses")
      case GetFundedAddresses =>
        RequestParam("getfundedaddresses")
      case GetUnusedAddresses =>
        RequestParam("getunusedaddresses")
      case GetAccounts =>
        RequestParam("getaccounts")
      case CreateNewAccount =>
        RequestParam("createnewaccount")
      case IsEmpty =>
        RequestParam("isempty")
      case GetBalance(isSats) =>
        RequestParam("getbalance", Seq(up.writeJs(isSats)))
      case GetConfirmedBalance(isSats) =>
        RequestParam("getconfirmedbalance", Seq(up.writeJs(isSats)))
      case GetUnconfirmedBalance(isSats) =>
        RequestParam("getunconfirmedbalance", Seq(up.writeJs(isSats)))
      case GetAddressInfo(address) =>
        RequestParam("getaddressinfo", Seq(up.writeJs(address)))
      case GetNewAddress =>
        RequestParam("getnewaddress")
      case Rescan(addressBatchSize,
                  startBlock,
                  endBlock,
                  force,
                  ignoreCreationTime) =>
        RequestParam("rescan",
                     Seq(up.writeJs(addressBatchSize),
                         up.writeJs(startBlock),
                         up.writeJs(endBlock),
                         up.writeJs(force),
                         up.writeJs(ignoreCreationTime)))

      case SendToAddress(address, bitcoins, satoshisPerVirtualByte) =>
        RequestParam("sendtoaddress",
                     Seq(up.writeJs(address),
                         up.writeJs(bitcoins),
                         up.writeJs(satoshisPerVirtualByte)))
      case SendFromOutPoints(outPoints, address, bitcoins, feeRateOpt) =>
        RequestParam("sendfromoutpoints",
                     Seq(up.writeJs(outPoints),
                         up.writeJs(address),
                         up.writeJs(bitcoins),
                         up.writeJs(feeRateOpt)))
      case SendWithAlgo(address, bitcoins, feeRateOpt, algo) =>
        RequestParam("SendWithAlgo",
                     Seq(up.writeJs(address),
                         up.writeJs(bitcoins),
                         up.writeJs(feeRateOpt),
                         up.writeJs(algo)))
      case OpReturnCommit(message, hashMessage, satoshisPerVirtualByte) =>
        RequestParam("opreturncommit",
                     Seq(up.writeJs(message),
                         up.writeJs(hashMessage),
                         up.writeJs(satoshisPerVirtualByte)))
      // height
      case GetBlockCount => RequestParam("getblockcount")
      // filter count
      case GetFilterCount => RequestParam("getfiltercount")
      // filter header count
      case GetFilterHeaderCount => RequestParam("getfilterheadercount")
      // besthash
      case GetBestBlockHash => RequestParam("getbestblockhash")
      // peers
      case GetPeers => RequestParam("getpeers")
      case Stop     => RequestParam("stop")
      case SendRawTransaction(tx) =>
        RequestParam("sendrawtransaction", Seq(up.writeJs(tx)))
      // PSBTs
      case CombinePSBTs(psbts) =>
        RequestParam("combinepsbts", Seq(up.writeJs(psbts)))
      case JoinPSBTs(psbts) =>
        RequestParam("joinpsbts", Seq(up.writeJs(psbts)))
      case FinalizePSBT(psbt) =>
        RequestParam("finalizepsbt", Seq(up.writeJs(psbt)))
      case ExtractFromPSBT(psbt) =>
        RequestParam("extractfrompsbt", Seq(up.writeJs(psbt)))
      case ConvertToPSBT(tx) =>
        RequestParam("converttopsbt", Seq(up.writeJs(tx)))

      case NoCommand => ???
    }

    Try {
      import com.softwaremill.sttp._
      implicit val backend: SttpBackend[Id, Nothing] =
        HttpURLConnectionBackend()
      val request =
        sttp
          .post(uri"http://$host:${config.rpcPort}/")
          .contentType("application/json")
          .body({
            val uuid = java.util.UUID.randomUUID.toString
            val paramsWithID: Map[String, ujson.Value] = requestParam.toJsonMap + ("id" -> up
              .writeJs(uuid))
            up.write(paramsWithID)
          })
      debug(s"HTTP request: $request")
      val response = request.send()

      debug(s"HTTP response:")
      debug(response)

      // in order to mimic Bitcoin Core we always send
      // an object looking like {"result": ..., "error": ...}
      val rawBody = response.body match {
        case Left(err)       => err
        case Right(response) => response
      }

      val js = ujson.read(rawBody)
      val jsObjT =
        Try(js.obj).transform[mutable.LinkedHashMap[String, ujson.Value]](
          Success(_),
          _ => error(s"Response was not a JSON object! Got: $rawBody"))

      /** Gets the given key from jsObj if it exists
        * and is not null */
      def getKey(key: String): Option[ujson.Value] = {
        jsObjT.toOption.flatMap(_.get(key).flatMap(result =>
          if (result.isNull) None else Some(result)))
      }

      /** Converts a `ujson.Value` to String, making an
        * effort to avoid preceding and trailing `"`s */
      def jsValueToString(value: ujson.Value) = value match {
        case Str(string)             => string
        case Num(num) if num.isWhole => num.toLong.toString
        case Num(num)                => num.toString
        case rest: ujson.Value       => rest.toString()
      }

      (getKey("result"), getKey("error")) match {
        case (Some(result), None) =>
          Success(jsValueToString(result))
        case (None, Some(err)) =>
          val msg = jsValueToString(err)
          error(msg)
        case (None, None) | (Some(_), Some(_)) =>
          error(s"Got unexpected response: $rawBody")
      }
    }.flatten
  }

  def host = "localhost"

  case class RequestParam(
      method: String,
      params: Seq[ujson.Value.Value] = Nil) {

    lazy val toJsonMap: Map[String, ujson.Value] = {
      Map("method" -> method, "params" -> params)
    }
  }
}

case class Config(
    command: CliCommand = CliCommand.NoCommand,
    network: Option[NetworkParameters] = None,
    debug: Boolean = false,
    rpcPort: Int = 9999
)

object Config {
  val empty = Config()
}

sealed abstract class CliCommand

object CliCommand {
  case object NoCommand extends CliCommand

  // Wallet
  case class SendToAddress(
      destination: BitcoinAddress,
      amount: Bitcoins,
      satoshisPerVirtualByte: Option[SatoshisPerVirtualByte])
      extends CliCommand
  case class SendFromOutPoints(
      outPoints: Vector[TransactionOutPoint],
      destination: BitcoinAddress,
      amount: Bitcoins,
      feeRateOpt: Option[SatoshisPerVirtualByte])
      extends CliCommand
  case class SendWithAlgo(
      destination: BitcoinAddress,
      amount: Bitcoins,
      feeRateOpt: Option[SatoshisPerVirtualByte],
      algo: CoinSelectionAlgo)
      extends CliCommand
  case class OpReturnCommit(
      message: String,
      hashMessage: Boolean,
      feeRateOpt: Option[SatoshisPerVirtualByte])
      extends CliCommand
  case object GetNewAddress extends CliCommand
  case object GetUtxos extends CliCommand
  case object GetAddresses extends CliCommand
  case object GetSpentAddresses extends CliCommand
  case object GetFundedAddresses extends CliCommand
  case object GetUnusedAddresses extends CliCommand
  case object GetAccounts extends CliCommand
  case object CreateNewAccount extends CliCommand
  case object IsEmpty extends CliCommand
  case class GetBalance(isSats: Boolean) extends CliCommand
  case class GetConfirmedBalance(isSats: Boolean) extends CliCommand
  case class GetUnconfirmedBalance(isSats: Boolean) extends CliCommand
  case class GetAddressInfo(address: BitcoinAddress) extends CliCommand

  // Node
  case object GetPeers extends CliCommand
  case object Stop extends CliCommand
  case class SendRawTransaction(tx: Transaction) extends CliCommand

  // Chain
  case object GetBestBlockHash extends CliCommand
  case object GetBlockCount extends CliCommand
  case object GetFilterCount extends CliCommand
  case object GetFilterHeaderCount extends CliCommand
  case class Rescan(
      addressBatchSize: Option[Int],
      startBlock: Option[BlockStamp],
      endBlock: Option[BlockStamp],
      force: Boolean,
      ignoreCreationTime: Boolean)
      extends CliCommand

  // PSBT
  case class CombinePSBTs(psbts: Seq[PSBT]) extends CliCommand
  case class JoinPSBTs(psbts: Seq[PSBT]) extends CliCommand
  case class FinalizePSBT(psbt: PSBT) extends CliCommand
  case class ExtractFromPSBT(psbt: PSBT) extends CliCommand
  case class ConvertToPSBT(transaction: Transaction) extends CliCommand
}