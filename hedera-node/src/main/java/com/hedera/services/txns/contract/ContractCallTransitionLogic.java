package com.hedera.services.txns.contract;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class ContractCallTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ContractCallTransitionLogic.class);

	private final LegacyCaller delegate;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final Supplier<SequenceNumber> seqNo;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> contracts;

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	public ContractCallTransitionLogic(
			LegacyCaller delegate,
			OptionValidator validator,
			TransactionContext txnCtx,
			Supplier<SequenceNumber> seqNo,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> contracts
	) {
		this.delegate = delegate;
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.seqNo = seqNo;
		this.contracts = contracts;
	}

	@FunctionalInterface
	public interface LegacyCaller {
		TransactionRecord perform(TransactionBody txn, Instant consensusTime, SequenceNumber seqNo);
	}

	@Override
	public void doStateTransition() {
		try {
			var contractCallTxn = txnCtx.accessor().getTxn();

			var legacyRecord = delegate.perform(contractCallTxn, txnCtx.consensusTime(), seqNo.get());

			txnCtx.setStatus(legacyRecord.getReceipt().getStatus());
			txnCtx.setCallResult(legacyRecord.getContractCallResult());
		} catch (Exception e) {
			log.warn("Avoidable exception!", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractCall;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody contractCallTxn) {
		var op = contractCallTxn.getContractCall();
		return validator.queryableContractStatus(op.getContractID(), contracts.get());
	}
}
