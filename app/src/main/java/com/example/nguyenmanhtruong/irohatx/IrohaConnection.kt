package com.example.nguyenmanhtruong.irohatx

import android.content.Context
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.reactivex.Single
import iroha.protocol.*
import jp.co.soramitsu.iroha.android.*
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import iroha.protocol.TransactionOuterClass

class IrohaConnection(context: Context) {

    private val crypto = ModelCrypto()
    private val txBuilder = ModelTransactionBuilder()
    private val queryBuilder = ModelQueryBuilder()
    private lateinit var protoTxHelper: ModelProtoTransaction
    private lateinit var protoQueryHelper: ModelProtoQuery
    private var channel = ManagedChannelBuilder.forAddress(context.getString(R.string.iroha_url),
        context.resources.getInteger(R.integer.iroha_port)).usePlaintext(true).build() as ManagedChannel

    fun execute(username: String, details: String): Single<String> {
        return Single.create { emitter ->
            val currentTime = System.currentTimeMillis()
            val userKeys = crypto.generateKeypair()
            val adminKeys = crypto.convertFromExisting(PUB_KEY, PRIV_KEY)

            // Create account
            val createAccount = txBuilder.creatorAccountId(CREATOR)
                    .createdTime(BigInteger.valueOf(currentTime))
                    .createAccount(username, DOMAIN_ID, userKeys.publicKey())
                    .build()

            // sign transaction and get its binary representation (Blob)
            protoTxHelper = ModelProtoTransaction(createAccount)
            var txblob = protoTxHelper.signAndAddSignature(adminKeys).finish().blob()

            // Convert ByteVector to byte array
            var bs = toByteArray(txblob)

            // create proto object
            var protoTx: TransactionOuterClass.Transaction? = null
            try {
                protoTx = TransactionOuterClass.Transaction.parseFrom(bs)
            } catch (e: InvalidProtocolBufferException) {
                System.err.println("Exception while converting byte array to protobuf:" + e.message)
                System.exit(1)
            }

            // Send transaction to iroha
            var stub = CommandServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            stub.torii(protoTx)

            // Check if it was successful
            if (!isTransactionSuccessful(stub, createAccount)) {
                emitter.onError(RuntimeException("Transaction failed"))
            }
            // Set account details
            val setDetailsTransaction = txBuilder.creatorAccountId("$username@$DOMAIN_ID")
                    .createdTime(BigInteger.valueOf(currentTime))
                    .setAccountDetail("$username@$DOMAIN_ID", "myFirstDetail", details)
                    .build()

            // sign transaction and get its binary representation (Blob)
            txblob = protoTxHelper.signAndAddSignature(userKeys).finish().blob()

            // Convert ByteVector to byte array
            bs = toByteArray(txblob)
            // create proto object
            try {
                protoTx = TransactionOuterClass.Transaction.parseFrom(bs)
            } catch (e: InvalidProtocolBufferException) {
                System.err.println("Exception while converting byte array to protobuf:" + e.message)
                System.exit(1)
            }

            // Send transaction to iroha
            stub = CommandServiceGrpc.newBlockingStub(channel)
            stub.torii(protoTx)

            // Check if
            // it was successful
            if (!isTransactionSuccessful(stub, setDetailsTransaction)) {
                emitter.onError(RuntimeException("Transaction failed"))
            }
            // Query the result
            val firstName = queryBuilder.creatorAccountId("$username@$DOMAIN_ID")
                    .queryCounter(BigInteger.valueOf(QUERY_COUNTER))
                    .createdTime(BigInteger.valueOf(currentTime))
                    .getAccountDetail("$username@$DOMAIN_ID")
                    .build()
            protoQueryHelper = ModelProtoQuery(firstName)
            val queryBlob = protoQueryHelper.signAndAddSignature(userKeys).finish().blob()
            val bquery = toByteArray(queryBlob)

            var protoQuery: Queries.Query? = null
            try {
                protoQuery = Queries.Query.parseFrom(bquery)
            } catch (e: InvalidProtocolBufferException) {
                emitter.onError(e)
            }

            val queryStub = QueryServiceGrpc.newBlockingStub(channel)
            val queryResponse = queryStub.find(protoQuery)

            emitter.onSuccess(queryResponse.accountDetailResponse.detail)
        }
    }

    private fun toByteArray(blob: ByteVector): ByteArray {
        val bs = ByteArray(blob.size().toInt())
        for (i in 0 until blob.size()) {
            bs[i.toInt()] = blob.get(i.toInt()).toByte()
        }
        return bs
    }

    private fun isTransactionSuccessful(stub: CommandServiceGrpc.CommandServiceBlockingStub, utx: UnsignedTx): Boolean {
        val txhash = utx.hash().blob()
        val bshash = toByteArray(txhash)

        val request = Endpoint.TxStatusRequest.newBuilder().setTxHash(ByteString.copyFrom(bshash)).build()

        val features = stub.statusStream(request)

        var response: Endpoint.ToriiResponse? = null
        while (features.hasNext()) {
            response = features.next()
        }

        return response!!.txStatus === Endpoint.TxStatus.COMMITTED
    }
}