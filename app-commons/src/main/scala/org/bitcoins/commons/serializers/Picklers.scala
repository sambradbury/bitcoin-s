package org.bitcoins.commons.serializers

import org.bitcoins.commons.jsonmodels.bitcoind.RpcOpts.LockUnspentOutputParameter
import org.bitcoins.core.api.wallet.CoinSelectionAlgo
import org.bitcoins.core.crypto.{
  ExtKey,
  ExtPrivateKey,
  ExtPublicKey,
  MnemonicCode
}
import org.bitcoins.core.currency.{Bitcoins, Satoshis}
import org.bitcoins.core.dlc.accounting.DLCWalletAccounting
import org.bitcoins.core.hd.AddressType
import org.bitcoins.core.number.UInt32
import org.bitcoins.core.protocol.dlc.models.DLCStatus._
import org.bitcoins.core.protocol.dlc.models._
import org.bitcoins.core.protocol.tlv._
import org.bitcoins.core.protocol.transaction.{Transaction, TransactionOutPoint}
import org.bitcoins.core.protocol.{BitcoinAddress, BlockStamp}
import org.bitcoins.core.psbt.InputPSBTRecord.PartialSignature
import org.bitcoins.core.psbt.PSBT
import org.bitcoins.core.util.TimeUtil
import org.bitcoins.core.wallet.fee.SatoshisPerVirtualByte
import org.bitcoins.core.wallet.utxo.AddressLabelTag
import org.bitcoins.crypto._
import scodec.bits.ByteVector
import ujson._
import upickle.default._

import java.io.File
import java.net.{InetSocketAddress, URI}
import java.nio.file.Path
import java.time.Instant
import java.util.Date

object Picklers {

  implicit val pathPickler: ReadWriter[Path] =
    readwriter[String].bimap(_.toString, str => new File(str).toPath)

  implicit val inetSocketAddress: ReadWriter[InetSocketAddress] =
    readwriter[String].bimap(
      addr => s"${addr.getHostName}:${addr.getPort}",
      str => {
        val uri = new URI("tcp://" + str)
        InetSocketAddress.createUnresolved(uri.getHost, uri.getPort)
      })

  implicit val byteVectorPickler: ReadWriter[ByteVector] =
    readwriter[String].bimap(_.toHex, str => ByteVector.fromValidHex(str))

  implicit val bitcoinAddressPickler: ReadWriter[BitcoinAddress] =
    readwriter[String]
      .bimap(_.value, BitcoinAddress.fromString)

  implicit val bitcoinsPickler: ReadWriter[Bitcoins] =
    readwriter[Double].bimap(_.toBigDecimal.toDouble, Bitcoins(_))

  implicit val satoshisPickler: ReadWriter[Satoshis] =
    readwriter[Long].bimap(_.toLong, Satoshis.apply)

  implicit val schnorrNoncePickler: ReadWriter[SchnorrNonce] =
    readwriter[String].bimap(_.hex, SchnorrNonce.fromHex)

  implicit val enumEventDescriptorPickler: ReadWriter[
    EnumEventDescriptorV0TLV] =
    readwriter[String].bimap(_.hex, EnumEventDescriptorV0TLV.fromHex)

  implicit val digitDecompEventDescriptorPickler: ReadWriter[
    DigitDecompositionEventDescriptorV0TLV] =
    readwriter[String].bimap(_.hex,
                             DigitDecompositionEventDescriptorV0TLV.fromHex)

  implicit val eventDescriptorPickler: ReadWriter[EventDescriptorTLV] =
    readwriter[String].bimap(_.hex, EventDescriptorTLV.fromHex)

  implicit val oracleEventVoPickler: ReadWriter[OracleEventV0TLV] =
    readwriter[String].bimap(_.hex, OracleEventV0TLV.fromHex)

  implicit val instantPickler: ReadWriter[Instant] =
    readwriter[Long].bimap(_.getEpochSecond, Instant.ofEpochSecond)

  implicit val datePickler: ReadWriter[Date] =
    readwriter[String].bimap(TimeUtil.iso8601ToString, TimeUtil.iso8601ToDate)

  implicit val aesPasswordPickler: ReadWriter[AesPassword] =
    readwriter[String].bimap(_.toStringSensitive, AesPassword.fromString)

  implicit val sha256DigestBEPickler: ReadWriter[Sha256DigestBE] =
    readwriter[String].bimap(_.hex, Sha256DigestBE.fromHex)

  implicit val sha256DigestPickler: ReadWriter[Sha256Digest] =
    readwriter[String].bimap(_.hex, Sha256Digest.fromHex)

  implicit val doubleSha256DigestBEPickler: ReadWriter[DoubleSha256DigestBE] =
    readwriter[String].bimap(_.hex, DoubleSha256DigestBE.fromHex)

  implicit val uInt32Pickler: ReadWriter[UInt32] =
    readwriter[Long].bimap(_.toLong, long => UInt32(long))

  implicit val satoshisPerVirtualBytePickler: ReadWriter[
    SatoshisPerVirtualByte] =
    readwriter[Long]
      .bimap(_.toLong, long => SatoshisPerVirtualByte(Satoshis(long)))

  implicit val oracleInfoPickler: ReadWriter[OracleInfo] =
    readwriter[String].bimap(_.hex, OracleInfo.fromHex)

  implicit val oracleAnnouncementPickler: ReadWriter[OracleAnnouncementTLV] =
    readwriter[String].bimap(_.hex, OracleAnnouncementTLV.fromHex)

  implicit val contractInfoPickler: ReadWriter[ContractInfo] =
    readwriter[String].bimap(_.hex, ContractInfo.fromHex)

  implicit val contractInfoTLVPickler: ReadWriter[ContractInfoV0TLV] =
    readwriter[String].bimap(_.hex, ContractInfoV0TLV.fromHex)

  implicit val schnorrDigitalSignaturePickler: ReadWriter[
    SchnorrDigitalSignature] =
    readwriter[String].bimap(_.hex, SchnorrDigitalSignature.fromHex)

  implicit val partialSignaturePickler: ReadWriter[PartialSignature] =
    readwriter[String].bimap(_.hex, PartialSignature.fromHex)

  implicit val dlcOfferTLVPickler: ReadWriter[DLCOfferTLV] =
    readwriter[String].bimap(_.hex, DLCOfferTLV.fromHex)

  implicit val lnMessageDLCOfferTLVPickler: ReadWriter[LnMessage[DLCOfferTLV]] =
    readwriter[String].bimap(_.hex, LnMessageFactory(DLCOfferTLV).fromHex)

  implicit val dlcAcceptTLVPickler: ReadWriter[DLCAcceptTLV] =
    readwriter[String].bimap(_.hex, DLCAcceptTLV.fromHex)

  implicit val lnMessageDLCAcceptTLVPickler: ReadWriter[
    LnMessage[DLCAcceptTLV]] =
    readwriter[String].bimap(_.hex, LnMessageFactory(DLCAcceptTLV).fromHex)

  implicit val dlcSignTLVPickler: ReadWriter[DLCSignTLV] =
    readwriter[String].bimap(_.hex, DLCSignTLV.fromHex)

  implicit val lnMessageDLCSignTLVPickler: ReadWriter[LnMessage[DLCSignTLV]] =
    readwriter[String].bimap(_.hex, LnMessageFactory(DLCSignTLV).fromHex)

  implicit val blockStampPickler: ReadWriter[BlockStamp] =
    readwriter[String].bimap(_.mkString, BlockStamp.fromString)

  implicit val psbtPickler: ReadWriter[PSBT] =
    readwriter[String].bimap(_.base64, PSBT.fromString)

  implicit val transactionPickler: ReadWriter[Transaction] =
    readwriter[String].bimap(_.hex, Transaction.fromHex)

  implicit val extPubKeyPickler: ReadWriter[ExtPublicKey] =
    readwriter[String].bimap(_.toString, ExtPublicKey.fromString)

  implicit val transactionOutPointPickler: ReadWriter[TransactionOutPoint] =
    readwriter[String].bimap(_.hex, TransactionOutPoint.fromHex)

  implicit val coinSelectionAlgoPickler: ReadWriter[CoinSelectionAlgo] =
    readwriter[String].bimap(_.toString, CoinSelectionAlgo.fromString)

  implicit val addressLabelTagPickler: ReadWriter[AddressLabelTag] =
    readwriter[String].bimap(_.name, AddressLabelTag)

  implicit val lockUnspentOutputParameterPickler: ReadWriter[
    LockUnspentOutputParameter] =
    readwriter[Value].bimap(_.toJson, LockUnspentOutputParameter.fromJson)

  implicit val offeredW: Writer[Offered] =
    writer[Obj].comap { offered =>
      import offered._
      Obj(
        "state" -> Str(statusString),
        "dlcId" -> Str(dlcId.hex),
        "isInitiator" -> Bool(isInitiator),
        "tempContractId" -> Str(tempContractId.hex),
        "contractInfo" -> Str(contractInfo.hex),
        "contractMaturity" -> Num(
          timeouts.contractMaturity.toUInt32.toLong.toDouble),
        "contractTimeout" -> Num(
          timeouts.contractTimeout.toUInt32.toLong.toDouble),
        "feeRate" -> Num(feeRate.toLong.toDouble),
        "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
        "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
        "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble)
      )
    }

  implicit val acceptedW: Writer[Accepted] = writer[Obj].comap { accepted =>
    import accepted._
    Obj(
      "state" -> Str(statusString),
      "dlcId" -> Str(dlcId.hex),
      "isInitiator" -> Bool(isInitiator),
      "tempContractId" -> Str(tempContractId.hex),
      "contractId" -> Str(contractId.toHex),
      "contractInfo" -> Str(contractInfo.hex),
      "contractMaturity" -> Num(
        timeouts.contractMaturity.toUInt32.toLong.toDouble),
      "contractTimeout" -> Num(
        timeouts.contractTimeout.toUInt32.toLong.toDouble),
      "feeRate" -> Num(feeRate.toLong.toDouble),
      "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
      "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
      "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble)
    )
  }

  implicit val signedW: Writer[Signed] = writer[Obj].comap { signed =>
    import signed._
    Obj(
      "state" -> Str(statusString),
      "dlcId" -> Str(dlcId.hex),
      "isInitiator" -> Bool(isInitiator),
      "tempContractId" -> Str(tempContractId.hex),
      "contractId" -> Str(contractId.toHex),
      "contractInfo" -> Str(contractInfo.hex),
      "contractMaturity" -> Num(
        timeouts.contractMaturity.toUInt32.toLong.toDouble),
      "contractTimeout" -> Num(
        timeouts.contractTimeout.toUInt32.toLong.toDouble),
      "feeRate" -> Num(feeRate.toLong.toDouble),
      "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
      "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
      "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble)
    )
  }

  implicit val broadcastedW: Writer[Broadcasted] =
    writer[Obj].comap { broadcasted =>
      import broadcasted._
      Obj(
        "state" -> Str(statusString),
        "dlcId" -> Str(dlcId.hex),
        "isInitiator" -> Bool(isInitiator),
        "tempContractId" -> Str(tempContractId.hex),
        "contractId" -> Str(contractId.toHex),
        "contractInfo" -> Str(contractInfo.hex),
        "contractMaturity" -> Num(
          timeouts.contractMaturity.toUInt32.toLong.toDouble),
        "contractTimeout" -> Num(
          timeouts.contractTimeout.toUInt32.toLong.toDouble),
        "feeRate" -> Num(feeRate.toLong.toDouble),
        "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
        "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
        "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble),
        "fundingTxId" -> Str(fundingTxId.hex)
      )
    }

  implicit val confirmedW: Writer[Confirmed] =
    writer[Obj].comap { confirmed =>
      import confirmed._
      Obj(
        "state" -> Str(statusString),
        "dlcId" -> Str(dlcId.hex),
        "isInitiator" -> Bool(isInitiator),
        "tempContractId" -> Str(tempContractId.hex),
        "contractId" -> Str(contractId.toHex),
        "contractInfo" -> Str(contractInfo.hex),
        "contractMaturity" -> Num(
          timeouts.contractMaturity.toUInt32.toLong.toDouble),
        "contractTimeout" -> Num(
          timeouts.contractTimeout.toUInt32.toLong.toDouble),
        "feeRate" -> Num(feeRate.toLong.toDouble),
        "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
        "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
        "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble),
        "fundingTxId" -> Str(fundingTxId.hex)
      )
    }

  private val counterPartyPayoutKey: String = "counterPartyPayout"

  implicit val claimedW: Writer[Claimed] = writer[Obj].comap { claimed =>
    import claimed._
    val (oraclesJs, outcomesJs) = oracleOutcome match {
      case EnumOracleOutcome(oracles, outcome) =>
        (Arr.from(oracles.map(o => Str(o.announcement.hex))),
         Str(outcome.outcome))
      case numeric: NumericOracleOutcome =>
        (Arr.from(numeric.oracles.map(_.announcement.hex)),
         Arr.from(numeric.outcomes.map(o => Arr.from(o.digits))))
    }

    Obj(
      "state" -> Str(statusString),
      "dlcId" -> Str(dlcId.hex),
      "isInitiator" -> Bool(isInitiator),
      "tempContractId" -> Str(tempContractId.hex),
      "contractId" -> Str(contractId.toHex),
      "contractInfo" -> Str(contractInfo.hex),
      "contractMaturity" -> Num(
        timeouts.contractMaturity.toUInt32.toLong.toDouble),
      "contractTimeout" -> Num(
        timeouts.contractTimeout.toUInt32.toLong.toDouble),
      "feeRate" -> Num(feeRate.toLong.toDouble),
      "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
      "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
      "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble),
      "fundingTxId" -> Str(fundingTxId.hex),
      "closingTxId" -> Str(closingTxId.hex),
      "oracleSigs" -> oracleSigs.map(sig => Str(sig.hex)),
      "outcomes" -> outcomesJs,
      "oracles" -> oraclesJs,
      PicklerKeys.myPayout -> Num(claimed.myPayout.satoshis.toLong.toDouble),
      counterPartyPayoutKey -> Num(
        claimed.counterPartyPayout.satoshis.toLong.toDouble),
      PicklerKeys.pnl -> Num(claimed.pnl.satoshis.toLong.toDouble),
      PicklerKeys.rateOfReturn -> Num(claimed.rateOfReturn.toDouble)
    )
  }

  implicit val remoteClaimedW: Writer[RemoteClaimed] =
    writer[Obj].comap { remoteClaimed =>
      import remoteClaimed._
      val (oraclesJs, outcomesJs) = oracleOutcome match {
        case EnumOracleOutcome(oracles, outcome) =>
          (Arr.from(oracles.map(o => Str(o.announcement.hex))),
           Str(outcome.outcome))
        case numeric: NumericOracleOutcome =>
          (Arr.from(numeric.oracles.map(_.announcement.hex)),
           Arr.from(numeric.outcomes.map(o => Arr.from(o.digits))))
      }

      Obj(
        "state" -> Str(statusString),
        "dlcId" -> Str(dlcId.hex),
        "isInitiator" -> Bool(isInitiator),
        "tempContractId" -> Str(tempContractId.hex),
        "contractId" -> Str(contractId.toHex),
        "contractInfo" -> Str(contractInfo.hex),
        "contractMaturity" -> Num(
          timeouts.contractMaturity.toUInt32.toLong.toDouble),
        "contractTimeout" -> Num(
          timeouts.contractTimeout.toUInt32.toLong.toDouble),
        "feeRate" -> Num(feeRate.toLong.toDouble),
        "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
        "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
        "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble),
        "fundingTxId" -> Str(fundingTxId.hex),
        "closingTxId" -> Str(closingTxId.hex),
        "oracleSigs" -> oracleSigs.map(sig => Str(sig.hex)),
        "outcomes" -> outcomesJs,
        "oracles" -> oraclesJs,
        PicklerKeys.myPayout -> Num(
          remoteClaimed.myPayout.satoshis.toLong.toDouble),
        counterPartyPayoutKey -> Num(
          remoteClaimed.counterPartyPayout.satoshis.toLong.toDouble),
        PicklerKeys.pnl -> Num(remoteClaimed.pnl.satoshis.toLong.toDouble),
        PicklerKeys.rateOfReturn -> Num(remoteClaimed.rateOfReturn.toDouble)
      )
    }

  implicit val refundedW: Writer[Refunded] = writer[Obj].comap { refunded =>
    import refunded._
    Obj(
      "state" -> Str(statusString),
      "dlcId" -> Str(dlcId.hex),
      "isInitiator" -> Bool(isInitiator),
      "tempContractId" -> Str(tempContractId.hex),
      "contractId" -> Str(contractId.toHex),
      "contractInfo" -> Str(contractInfo.hex),
      "contractMaturity" -> Num(
        timeouts.contractMaturity.toUInt32.toLong.toDouble),
      "contractTimeout" -> Num(
        timeouts.contractTimeout.toUInt32.toLong.toDouble),
      "feeRate" -> Num(feeRate.toLong.toDouble),
      "totalCollateral" -> Num(totalCollateral.satoshis.toLong.toDouble),
      "localCollateral" -> Num(localCollateral.satoshis.toLong.toDouble),
      "remoteCollateral" -> Num(remoteCollateral.satoshis.toLong.toDouble),
      "fundingTxId" -> Str(fundingTxId.hex),
      "closingTxId" -> Str(closingTxId.hex),
      PicklerKeys.myPayout -> Num(refunded.myPayout.satoshis.toLong.toDouble),
      counterPartyPayoutKey -> Num(
        refunded.counterPartyPayout.satoshis.toLong.toDouble),
      PicklerKeys.pnl -> Num(refunded.pnl.satoshis.toLong.toDouble),
      PicklerKeys.rateOfReturn -> Num(refunded.rateOfReturn.toDouble)
    )
  }

  implicit val dlcStatusW: Writer[DLCStatus] = writer[Value].comap {
    case o: Offered =>
      writeJs(o)(offeredW)
    case a: Accepted =>
      writeJs(a)(acceptedW)
    case s: Signed =>
      writeJs(s)(signedW)
    case b: Broadcasted =>
      writeJs(b)(broadcastedW)
    case c: Confirmed =>
      writeJs(c)(confirmedW)
    case c: Claimed =>
      writeJs(c)(claimedW)
    case r: RemoteClaimed =>
      writeJs(r)(remoteClaimedW)
    case r: Refunded =>
      writeJs(r)(refundedW)
  }

  implicit val dlcStatusR: Reader[DLCStatus] = reader[Obj].map { obj =>
    val dlcId = Sha256Digest(obj("dlcId").str)
    val state = DLCState.fromString(obj("state").str)
    val isInitiator = obj("isInitiator").bool
    val tempContractId = Sha256Digest(obj("tempContractId").str)
    val contractInfoTLV = ContractInfoV0TLV(obj("contractInfo").str)
    val contractMaturity =
      BlockStamp(UInt32(obj("contractMaturity").num.toLong))
    val contractTimeout = BlockStamp(UInt32(obj("contractTimeout").num.toLong))
    val feeRate = SatoshisPerVirtualByte.fromLong(obj("feeRate").num.toLong)
    val totalCollateral = Satoshis(obj("totalCollateral").num.toLong)
    val localCollateral = Satoshis(obj("localCollateral").num.toLong)

    lazy val contractId = ByteVector.fromValidHex(obj("contractId").str)
    lazy val fundingTxId = DoubleSha256DigestBE(obj("fundingTxId").str)
    lazy val closingTxId = DoubleSha256DigestBE(obj("closingTxId").str)
    lazy val oracleSigs =
      obj("oracleSigs").arr
        .map(value => SchnorrDigitalSignature(value.str))
        .toVector

    lazy val outcomesJs = obj("outcomes")
    lazy val outcomes = outcomesJs.strOpt match {
      case Some(value) => Vector(EnumOutcome(value))
      case None =>
        outcomesJs.arr.map { outcomeJs =>
          val digits = outcomeJs.arr.map(value => value.num.toInt)
          UnsignedNumericOutcome(digits.toVector)
        }.toVector
    }

    lazy val oraclesJs = obj("oracles")
    lazy val oracles = oraclesJs.arr.map { value =>
      val announcementTLV = OracleAnnouncementTLV(value.str)
      SingleOracleInfo(announcementTLV)
    }.toVector

    lazy val oracleOutcome = outcomes.head match {
      case outcome: EnumOutcome =>
        EnumOracleOutcome(oracles.asInstanceOf[Vector[EnumSingleOracleInfo]],
                          outcome)
      case UnsignedNumericOutcome(_) =>
        val numericOutcomes =
          outcomes.map(_.asInstanceOf[UnsignedNumericOutcome])
        val numericOracles =
          oracles.map(_.asInstanceOf[NumericSingleOracleInfo])
        NumericOracleOutcome(numericOracles.zip(numericOutcomes))
      case signed: SignedNumericOutcome =>
        throw new IllegalArgumentException(s"Unexpected outcome $signed")
    }

    lazy val myPayoutJs = obj(PicklerKeys.myPayout)
    lazy val myPayoutOpt = myPayoutJs.numOpt.map(sats => Satoshis(sats.toLong))
    lazy val theirPayoutJs = obj(counterPartyPayoutKey)
    lazy val theirPayoutOpt =
      theirPayoutJs.numOpt.map(sats => Satoshis(sats.toLong))

    state match {
      case DLCState.Offered =>
        Offered(
          dlcId,
          isInitiator,
          tempContractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral
        )
      case DLCState.Accepted =>
        Accepted(
          dlcId,
          isInitiator,
          tempContractId,
          contractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral
        )
      case DLCState.Signed =>
        Signed(
          dlcId,
          isInitiator,
          tempContractId,
          contractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral
        )
      case DLCState.Broadcasted =>
        Broadcasted(
          dlcId,
          isInitiator,
          tempContractId,
          contractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral,
          fundingTxId
        )
      case DLCState.Confirmed =>
        Confirmed(
          dlcId,
          isInitiator,
          tempContractId,
          contractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral,
          fundingTxId
        )
      case DLCState.Claimed =>
        Claimed(
          dlcId,
          isInitiator,
          tempContractId,
          contractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral,
          fundingTxId,
          closingTxId,
          oracleSigs,
          oracleOutcome,
          myPayout = myPayoutOpt.get,
          counterPartyPayout = theirPayoutOpt.get
        )
      case DLCState.RemoteClaimed =>
        require(oracleSigs.size == 1,
                "Remote claimed should only have one oracle sig")
        RemoteClaimed(
          dlcId,
          isInitiator,
          tempContractId,
          contractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral,
          fundingTxId,
          closingTxId,
          oracleSigs.head,
          oracleOutcome,
          myPayout = myPayoutOpt.get,
          counterPartyPayout = theirPayoutOpt.get
        )
      case DLCState.Refunded =>
        Refunded(
          dlcId,
          isInitiator,
          tempContractId,
          contractId,
          ContractInfo.fromTLV(contractInfoTLV),
          DLCTimeouts(contractMaturity, contractTimeout),
          feeRate,
          totalCollateral,
          localCollateral,
          fundingTxId,
          closingTxId,
          myPayout = myPayoutOpt.get,
          counterPartyPayout = theirPayoutOpt.get
        )
    }
  }

  implicit val dlcWalletAccountingWriter: Writer[DLCWalletAccounting] = {
    writer[Obj].comap { walletAccounting: DLCWalletAccounting =>
      Obj(
        PicklerKeys.myCollateral -> Num(
          walletAccounting.myCollateral.satoshis.toLong.toDouble),
        PicklerKeys.theirCollateral -> Num(
          walletAccounting.theirCollateral.satoshis.toLong.toDouble),
        PicklerKeys.myPayout -> Num(
          walletAccounting.myPayout.satoshis.toLong.toDouble),
        PicklerKeys.theirPayout -> Num(
          walletAccounting.theirPayout.satoshis.toLong.toDouble),
        PicklerKeys.pnl -> Num(walletAccounting.pnl.satoshis.toLong.toDouble),
        PicklerKeys.rateOfReturn -> Num(walletAccounting.rateOfReturn.toDouble)
      )
    }
  }

  implicit val mnemonicCodePickler: ReadWriter[MnemonicCode] =
    readwriter[String].bimap(
      _.words.mkString(" "),
      str => MnemonicCode.fromWords(str.split(' ').toVector))

  implicit val extPrivateKeyPickler: ReadWriter[ExtPrivateKey] =
    readwriter[String].bimap(ExtKey.toString, ExtPrivateKey.fromString)

  implicit val oracleAttestmentTLV: ReadWriter[OracleAttestmentTLV] =
    readwriter[String].bimap(_.hex, OracleAttestmentTLV.fromHex)

  implicit val ecPublicKeyPickler: ReadWriter[ECPublicKey] =
    readwriter[String].bimap(_.hex, ECPublicKey.fromHex)

  implicit val addressTypePickler: ReadWriter[AddressType] =
    readwriter[String].bimap(_.toString, AddressType.fromString)
}
