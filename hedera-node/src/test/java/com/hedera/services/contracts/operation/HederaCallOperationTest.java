package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

import static com.hedera.services.contracts.operation.CommonCallSetup.commonSetup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HederaCallOperationTest {
	@Mock
	private GasCalculator calc;
	@Mock
	private MessageFrame evmMsgFrame;
	@Mock
	private EVM evm;
	@Mock
	private HederaStackedWorldStateUpdater worldUpdater;
	@Mock
	private Account acc;
	@Mock
	private Address accountAddr;
	@Mock
	private Gas cost;
	@Mock
	private EvmSigsVerifier sigsVerifier;
	@Mock
	private BiPredicate<Address, MessageFrame> addressValidator;
	@Mock
	private Map<String, PrecompiledContract> precompiledContractMap;

	private HederaCallOperation subject;

	@BeforeEach
	void setup() {
		subject = new HederaCallOperation(sigsVerifier, calc, addressValidator, precompiledContractMap);
	}

	@Test
	void usesCanonicalAddressFromSuperNominalIfNotPrecompile() {
		final var nominal = Address.ALTBN128_ADD;
		final var canonical = Address.BLS12_G1MUL;
		given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.wrap(nominal.toArrayUnsafe()));
		given(evmMsgFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.priorityAddress(nominal)).willReturn(canonical);

		final var actual = subject.address(evmMsgFrame);

		assertEquals(actual, canonical);
	}

	@Test
	void usesSuperNominalIfPrecompile() {
		final var nominal = Address.ALTBN128_ADD;
		given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.wrap(nominal.toArrayUnsafe()));
		given(precompiledContractMap.containsKey(nominal.toShortHexString())).willReturn(true);

		final var actual = subject.address(evmMsgFrame);

		assertEquals(actual, nominal);
	}

	@Test
	void haltWithInvalidAddr() {
		commonSetup(evmMsgFrame, worldUpdater, acc);
		given(worldUpdater.get(any())).willReturn(null);
		given(calc.callOperationGasCost(
				any(), any(), anyLong(),
				anyLong(), anyLong(), anyLong(),
				any(), any(), any())
		).willReturn(cost);
		given(evmMsgFrame.getStackItem(0)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(2)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(3)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(4)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(5)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(6)).willReturn(Bytes.EMPTY);
		given(addressValidator.test(any(), any())).willReturn(false);

		var opRes = subject.execute(evmMsgFrame, evm);

		assertEquals(opRes.getHaltReason(), Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		assertEquals(opRes.getGasCost().get(), cost);
	}

	@Test
	void executesAsExpected() {
		commonSetup(evmMsgFrame, worldUpdater, acc);
		given(calc.callOperationGasCost(
				any(), any(), anyLong(),
				anyLong(), anyLong(), anyLong(),
				any(), any(), any())
		).willReturn(cost);
		// and:
		given(evmMsgFrame.getStackItem(0)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(1)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(2)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(3)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(4)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(5)).willReturn(Bytes.EMPTY);
		given(evmMsgFrame.getStackItem(6)).willReturn(Bytes.EMPTY);
		// and:
		given(evmMsgFrame.stackSize()).willReturn(20);
		given(evmMsgFrame.getRemainingGas()).willReturn(cost);
		given(evmMsgFrame.getMessageStackDepth()).willReturn(1025);

		given(evmMsgFrame.getContractAddress()).willReturn(Address.ALTBN128_ADD);
		given(evmMsgFrame.getRecipientAddress()).willReturn(Address.ALTBN128_ADD);

		given(worldUpdater.get(any())).willReturn(acc);
		given(acc.getBalance()).willReturn(Wei.of(100));
		given(calc.gasAvailableForChildCall(any(), any(), anyBoolean())).willReturn(Gas.of(10));
		given(acc.getAddress()).willReturn(accountAddr);
		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(Mockito.anyBoolean(), any(), any(), any())).willReturn(true);
		given(addressValidator.test(any(), any())).willReturn(true);

		var opRes = subject.execute(evmMsgFrame, evm);
		assertEquals(Optional.empty(), opRes.getHaltReason());
		assertEquals(opRes.getGasCost().get(), cost);

		given(sigsVerifier.hasActiveKeyOrNoReceiverSigReq(Mockito.anyBoolean(), any(),  any(), any())).willReturn(false);
		var invalidSignaturesRes = subject.execute(evmMsgFrame, evm);
		assertEquals(Optional.of(HederaExceptionalHaltReason.INVALID_SIGNATURE), invalidSignaturesRes.getHaltReason());
	}
}
