package com.hedera.services.txns.crypto.helpers;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.GrantedCryptoAllowance;
import com.hederahashgraph.api.proto.java.GrantedNftAllowance;
import com.hederahashgraph.api.proto.java.GrantedTokenAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.models.Id.MISSING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

public class AllowanceHelpers {
	private AllowanceHelpers() {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Since each serial number in an NFTAllowance is considered as an allowance, to get total allowance
	 * from an NFTAllowance the size of serial numbers should be added.
	 *
	 * @param nftAllowances
	 * @return
	 */
	public static int aggregateNftAllowances(List<NftAllowance> nftAllowances) {
		int nftAllowancesTotal = 0;
		for (var allowances : nftAllowances) {
			var serials = allowances.getSerialNumbersList();
			if (!serials.isEmpty()) {
				nftAllowancesTotal += serials.size();
			} else {
				nftAllowancesTotal++;
			}
		}
		return nftAllowancesTotal;
	}

	public static boolean hasRepeatedSpender(List<EntityNumPair> spenders) {
		final int n = spenders.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				if (spenders.get(i).equals(spenders.get(j))) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean hasRepeatedSerials(List<Long> serials) {
		final int n = serials.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				if (absolute(serials.get(i)).equals(absolute(serials.get(j)))) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean hasRepeatedId(List<Pair<EntityNum, FcTokenAllowanceId>> allowanceKeys) {
		final int n = allowanceKeys.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				final var a = allowanceKeys.get(i);
				final var b = allowanceKeys.get(j);
				if (a.getLeft().equals(b.getLeft()) && a.getRight().equals(b.getRight())) {
					return true;
				}
			}
		}
		return false;
	}

	public static int countSerials(final List<NftAllowance> nftAllowancesList) {
		int totalSerials = 0;
		for (var allowance : nftAllowancesList) {
			totalSerials += allowance.getSerialNumbersCount();
		}
		return totalSerials;
	}

	public static Long absolute(Long val) {
		return val < 0 ? val * -1 : val;
	}

	public static List<GrantedNftAllowance> getNftAllowancesList(final MerkleAccount account) {
		if (!account.getApproveForAllNfts().isEmpty()) {
			List<GrantedNftAllowance> nftAllowances = new ArrayList<>();
			for (var a : account.getApproveForAllNfts()) {
				final var approveForAllNftsAllowance = GrantedNftAllowance.newBuilder();
				approveForAllNftsAllowance.setTokenId(a.getTokenNum().toGrpcTokenId());
				approveForAllNftsAllowance.setSpender(a.getSpenderNum().toGrpcAccountId());
				nftAllowances.add(approveForAllNftsAllowance.build());
			}
			return nftAllowances;
		}
		return Collections.emptyList();
	}

	public static List<GrantedTokenAllowance> getFungibleTokenAllowancesList(final MerkleAccount account) {
		if (!account.getFungibleTokenAllowances().isEmpty()) {
			List<GrantedTokenAllowance> tokenAllowances = new ArrayList<>();
			final var tokenAllowance = GrantedTokenAllowance.newBuilder();
			for (var a : account.getFungibleTokenAllowances().entrySet()) {
				tokenAllowance.setTokenId(a.getKey().getTokenNum().toGrpcTokenId());
				tokenAllowance.setSpender(a.getKey().getSpenderNum().toGrpcAccountId());
				tokenAllowance.setAmount(a.getValue());
				tokenAllowances.add(tokenAllowance.build());
			}
			return tokenAllowances;
		}
		return Collections.emptyList();
	}

	public static List<GrantedCryptoAllowance> getCryptoAllowancesList(final MerkleAccount account) {
		if (!account.getCryptoAllowances().isEmpty()) {
			List<GrantedCryptoAllowance> cryptoAllowances = new ArrayList<>();
			final var cryptoAllowance = GrantedCryptoAllowance.newBuilder();
			for (var a : account.getCryptoAllowances().entrySet()) {
				cryptoAllowance.setSpender(a.getKey().toGrpcAccountId());
				cryptoAllowance.setAmount(a.getValue());
				cryptoAllowances.add(cryptoAllowance.build());
			}
			return cryptoAllowances;
		}
		return Collections.emptyList();
	}

	/**
	 * Returns owner account to be considered for the allowance changes. If the owner is missing in allowance,
	 * considers payer of the transaction as the owner. This is same for CryptoApproveAlowance, CryptoAdjustAllowance
	 * and CryptoDeleteAllowance transaction.Looks at entitiesChanged map before fetching from accountStore for
	 * performance.
	 *
	 * @param owner
	 * 		given owner
	 * @param payerAccount
	 * 		given payer for the transaction
	 * @param accountStore
	 * 		account store
	 * @param entitiesChanged
	 * 		map of all entities that are changed
	 * @return owner account
	 */
	public static Account fetchOwnerAccount(final AccountID owner,
			final Account payerAccount,
			final AccountStore accountStore,
			final Map<Long, Account> entitiesChanged) {
		final var ownerId = Id.fromGrpcAccount(owner);
		if (owner.equals(AccountID.getDefaultInstance()) || owner.equals(payerAccount.getId().asGrpcAccount())) {
			return payerAccount;
		} else if (entitiesChanged.containsKey(ownerId.num())) {
			return entitiesChanged.get(ownerId.num());
		} else {
			return accountStore.loadAccountOrFailWith(ownerId, INVALID_ALLOWANCE_OWNER_ID);
		}
	}

	public static EntityNumPair buildEntityNumPairFrom(AccountID owner, AccountID spender, final EntityNum payer) {
		return EntityNumPair.fromLongs(owner == null ? payer.longValue() : owner.getAccountNum(),
				spender.getAccountNum());
	}

	public static Pair<EntityNum, FcTokenAllowanceId> buildTokenAllowanceKey
			(AccountID owner, TokenID token, AccountID spender) {
		return Pair.of(EntityNum.fromAccountId(owner), FcTokenAllowanceId.from(EntityNum.fromTokenId(token),
				EntityNum.fromAccountId(spender)));
	}

	/**
	 * Updates the Spender of each NFT serial
	 *
	 * @param tokenStore
	 * 		The tokenStore to load UniqueToken and Token models to validate and update the spender.
	 * @param ownerId
	 * 		The owner Id of the NFT serials
	 * @param spenderId
	 * 		The spender to be set for the NFT serials
	 * @param tokenId
	 * 		The token ID of the NFT type.
	 * @param serialNums
	 * 		The serial numbers of the NFT type to update the spender.
	 * @return A list of UniqueTokens that we updated.
	 */
	public static List<UniqueToken> updateSpender(
			final TypedTokenStore tokenStore,
			final Id ownerId,
			final Id spenderId,
			final Id tokenId,
			final List<Long> serialNums) {
		if (serialNums.isEmpty()) {
			return Collections.emptyList();
		}

		final var nfts = new ArrayList<UniqueToken>();
		for (var serialNum : serialNums) {
			final var nft = tokenStore.loadUniqueToken(tokenId, serialNum);
			final var token = tokenStore.loadPossiblyPausedToken(tokenId);
			validateTrue(validOwner(nft, ownerId, token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
			nft.setSpender(spenderId);
			nfts.add(nft);
		}
		return nfts;
	}

	/**
	 * Checks the owner of token is treasury or the owner id given in allowance. If not, considers as an invalid owner
	 * and returns false.
	 *
	 * @param nft
	 * 		given nft
	 * @param ownerId
	 * 		owner given in allowance
	 * @param token
	 * 		token for which nft belongs to
	 * @return
	 */
	public static boolean validOwner(final UniqueToken nft, final Id ownerId, final Token token) {
		final var listedOwner = nft.getOwner();
		return MISSING_ID.equals(listedOwner)
				? ownerId.equals(token.getTreasury().getId())
				: listedOwner.equals(ownerId);
	}

	/**
	 * Checks if the total allowances of an account will exceed the limit after applying this transaction.
	 * This limit doesn't include number of serials for nfts, since they are  not stored on account. The limit
	 * includes number of crypto allowances, number of fungible token allowances and number of approvedForAll Nft
	 * allowances on owner account
	 *
	 * @param owner
	 * 		The Account to validate the allowances limit on.
	 * @param maxAllowanceLimitPerAccount
	 * 		The maximum number of allowances an Account can have.
	 */
	public static void validateAllowanceLimitsOn(final Account owner, final int maxAllowanceLimitPerAccount) {
		validateFalse(owner.getTotalAllowances() > maxAllowanceLimitPerAccount, MAX_ALLOWANCES_EXCEEDED);
	}
}
