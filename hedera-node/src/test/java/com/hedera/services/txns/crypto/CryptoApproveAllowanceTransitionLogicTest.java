package com.hedera.services.txns.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.BoolValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CryptoApproveAllowanceTransitionLogicTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountStore accountStore;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private ApproveAllowanceChecks allowanceChecks;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private StateView view;

	private TransactionBody cryptoApproveAllowanceTxn;
	private CryptoApproveAllowanceTransactionBody op;

	CryptoApproveAllowanceTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new CryptoApproveAllowanceTransitionLogic(txnCtx, accountStore, tokenStore, allowanceChecks,
				dynamicProperties, view);
		nft1.setOwner(Id.fromGrpcAccount(ownerId));
		nft2.setOwner(Id.fromGrpcAccount(ownerId));
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoApproveAllowanceTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void happyPathAddsAllowances() {
		givenValidTxnCtx();

		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);

		subject.doStateTransition();

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());

		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void considersPayerAsOwnerIfNotMentioned() {
		givenValidTxnCtxWithOwnerAsPayer();
		nft1.setOwner(Id.fromGrpcAccount(payerId));
		nft2.setOwner(Id.fromGrpcAccount(payerId));

		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);

		assertEquals(0, payerAcccount.getCryptoAllowances().size());
		assertEquals(0, payerAcccount.getFungibleTokenAllowances().size());
		assertEquals(0, payerAcccount.getApprovedForAllNftsAllowances().size());

		subject.doStateTransition();

		assertEquals(1, payerAcccount.getCryptoAllowances().size());
		assertEquals(1, payerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, payerAcccount.getApprovedForAllNftsAllowances().size());
		assertEquals(spenderId1, nft1.getSpender());
		assertEquals(spenderId1, nft2.getSpender());

		verify(accountStore).commitAccount(payerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void wipesSerialsWhenApprovedForAll() {
		givenValidTxnCtx();

		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);

		subject.doStateTransition();

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());
		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void checksIfAllowancesExceedLimit() {
		Account owner = mock(Account.class);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(owner);
		given(owner.getTotalAllowances()).willReturn(101);

		givenValidTxnCtx();

		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		var exception = assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());
		assertEquals(MAX_ALLOWANCES_EXCEEDED, exception.getResponseCode());
		assertEquals(0, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAcccount.getApprovedForAllNftsAllowances().size());
		verify(accountStore, never()).commitAccount(ownerAcccount);
	}

	@Test
	void semanticCheckDelegatesWorks() {
		givenValidTxnCtx();
		given(allowanceChecks.allowancesValidation(
				op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(),
				op.getNftAllowancesList(),
				payerAcccount,
				view))
				.willReturn(OK);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		assertEquals(OK, subject.semanticCheck().apply(cryptoApproveAllowanceTxn));
	}

	@Test
	void emptyAllowancesInStateTransitionWorks() {
		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
				).build();

		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);

		subject.doStateTransition();
		assertEquals(0, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(0, ownerAcccount.getApprovedForAllNftsAllowances().size());
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}


	@Test
	void removesAllowancesWhenAmountIsZero() {
		givenTxnCtxWithZeroAmount();
		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);

		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);

		subject.doStateTransition();

		assertEquals(0, ownerAcccount.getCryptoAllowances().size());
		assertEquals(0, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());
		assertEquals(spenderId1, nft1.getSpender());
		assertEquals(spenderId1, nft2.getSpender());

		verify(accountStore).commitAccount(ownerAcccount);
		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void skipsTxnWhenKeyExistsAndAmountGreaterThanZero() {
		var ownerAcccount = new Account(Id.fromGrpcAccount(ownerId));
		setUpOwnerWithExistingKeys(ownerAcccount);

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());

		givenValidTxnCtx();

		given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
		given(accountStore.loadAccountOrFailWith(ownerAcccount.getId(), INVALID_ALLOWANCE_OWNER_ID))
				.willReturn(ownerAcccount);
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);

		subject.doStateTransition();

		assertEquals(1, ownerAcccount.getCryptoAllowances().size());
		assertEquals(1, ownerAcccount.getFungibleTokenAllowances().size());
		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());

		verify(txnCtx).setStatus(ResponseCodeEnum.SUCCESS);
	}

	@Test
	void checkIfApproveForAllIsSet(){
		final NftAllowance nftAllowance = NftAllowance.newBuilder()
				.setSpender(spender1)
				.setOwner(ownerId)
				.setTokenId(token2)
				.addAllSerialNumbers(List.of(serial1)).build();
		final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
				.setSpender(spender1)
				.setOwner(ownerId)
				.setTokenId(token2)
				.setApprovedForAll(BoolValue.of(false))
				.addAllSerialNumbers(List.of(serial1)).build();
		nftAllowances.add(nftAllowance);
		nftAllowances.add(nftAllowance1);

		var ownerAcccount = new Account(Id.fromGrpcAccount(ownerId));

		givenValidTxnCtx();

		given(accountStore.loadAccountOrFailWith(spenderId1, INVALID_ALLOWANCE_SPENDER_ID))
				.willReturn(payerAcccount);
		ownerAcccount.setCryptoAllowances(new TreeMap<>());
		ownerAcccount.setFungibleTokenAllowances(new TreeMap<>());
		ownerAcccount.setApproveForAllNfts(new TreeSet<>());
		given(dynamicProperties.maxAllowanceLimitPerAccount()).willReturn(100);
		given(tokenStore.loadUniqueToken(tokenId2, serial1)).willReturn(nft1);
		given(tokenStore.loadUniqueToken(tokenId2, serial2)).willReturn(nft2);

		subject.applyNftAllowances(nftAllowances, ownerAcccount);

		assertEquals(1, ownerAcccount.getApprovedForAllNftsAllowances().size());
	}

	private void setUpOwnerWithExistingKeys(final Account ownerAcccount) {
		Map<EntityNum, Long> cryptoAllowances = new TreeMap<>();
		Map<FcTokenAllowanceId, Long> tokenAllowances = new TreeMap<>();
		Set<FcTokenAllowanceId> nftAllowances = new TreeSet<>();
		final var id = FcTokenAllowanceId.from(EntityNum.fromTokenId(token1),
				EntityNum.fromAccountId(spender1));
		final var Nftid = FcTokenAllowanceId.from(EntityNum.fromTokenId(token2),
				EntityNum.fromAccountId(spender1));
		cryptoAllowances.put(EntityNum.fromAccountId(spender1), 10000L);
		tokenAllowances.put(id, 100000L);
		nftAllowances.add(Nftid);
		ownerAcccount.setCryptoAllowances(cryptoAllowances);
		ownerAcccount.setFungibleTokenAllowances(tokenAllowances);
		ownerAcccount.setApproveForAllNfts(nftAllowances);
	}

	private void givenTxnCtxWithZeroAmount() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		final CryptoAllowance cryptoAllowance = CryptoAllowance
				.newBuilder()
				.setOwner(ownerId)
				.setSpender(spender1)
				.setAmount(0L).build();
		final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
				.setSpender(spender1)
				.setAmount(0L)
				.setTokenId(token1)
				.setOwner(ownerId)
				.build();
		final NftAllowance nftAllowance = NftAllowance.newBuilder()
				.setSpender(spender1)
				.setTokenId(token2)
				.setApprovedForAll(BoolValue.of(false))
				.setOwner(ownerId)
				.addAllSerialNumbers(List.of(1L, 10L))
				.build();

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		cryptoAllowances.add(cryptoAllowance);
		tokenAllowances.add(tokenAllowance);
		nftAllowances.add(nftAllowance);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();

		ownerAcccount.setApproveForAllNfts(new TreeSet<>());
		ownerAcccount.setCryptoAllowances(new HashMap<>());
		ownerAcccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void givenValidTxnCtx() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

		ownerAcccount.setApproveForAllNfts(new TreeSet<>());
		ownerAcccount.setCryptoAllowances(new HashMap<>());
		ownerAcccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private void givenValidTxnCtxWithOwnerAsPayer() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
				.setSpender(spender1)
				.setAmount(10L).build();
		final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
				.setSpender(spender1)
				.setAmount(10L)
				.setTokenId(token1)
				.build();
		final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
				.setSpender(spender1)
				.setTokenId(token2)
				.setApprovedForAll(BoolValue.of(true))
				.addAllSerialNumbers(List.of(1L, 10L)).build();

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);

		cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoApproveAllowance(
						CryptoApproveAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

		payerAcccount.setApproveForAllNfts(new TreeSet<>());
		payerAcccount.setCryptoAllowances(new HashMap<>());
		payerAcccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payerId)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private static final long serial1 = 1L;
	private static final long serial2 = 10L;
	private static final AccountID spender1 = asAccount("0.0.123");
	private static final TokenID token1 = asToken("0.0.100");
	private static final TokenID token2 = asToken("0.0.200");
	private static final AccountID payerId = asAccount("0.0.5000");
	private static final AccountID ownerId = asAccount("0.0.6000");
	private static final Id tokenId1 = Id.fromGrpcToken(token1);
	private static final Id tokenId2 = Id.fromGrpcToken(token2);
	private static final Id spenderId1 = Id.fromGrpcAccount(spender1);
	private static final Instant consensusTime = Instant.now();
	private final Token token1Model = new Token(Id.fromGrpcToken(token1));
	private final Token token2Model = new Token(Id.fromGrpcToken(token2));
	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
			.setSpender(spender1)
			.setOwner(ownerId)
			.setAmount(10L).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
			.setSpender(spender1)
			.setAmount(10L)
			.setTokenId(token1)
			.setOwner(ownerId)
			.build();
	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
			.setSpender(spender1)
			.setOwner(ownerId)
			.setTokenId(token2)
			.setApprovedForAll(BoolValue.of(true))
			.addAllSerialNumbers(List.of(serial1, serial2)).build();
	private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private List<NftAllowance> nftAllowances = new ArrayList<>();
	private final Account payerAcccount = new Account(Id.fromGrpcAccount(payerId));
	private final Account ownerAcccount = new Account(Id.fromGrpcAccount(ownerId));
	private final UniqueToken nft1 = new UniqueToken(tokenId1, serial1);
	private final UniqueToken nft2 = new UniqueToken(tokenId2, serial2);
}
