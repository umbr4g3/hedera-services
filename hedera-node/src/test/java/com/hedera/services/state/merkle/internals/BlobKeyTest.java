package com.hedera.services.state.merkle.internals;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BlobKeyTest {
	private BlobKey subject;

	@BeforeEach
	void setup() {
		subject = new BlobKey(BlobKey.BlobType.FILE_DATA, 2);
	}

	@Test
	void gettersWork() {
		// expect:
		assertEquals(BlobKey.BlobType.FILE_DATA, subject.getType());
		assertEquals(2, subject.getEntityNum());
	}


	@Test
	void toStringWorks() {
		assertEquals(
				"BlobKey{type=FILE_DATA, entityNum=2}",
				subject.toString());
	}

	@Test
	void objectContractMet() {
		final var one = new BlobKey(BlobKey.BlobType.FILE_METADATA, 2);
		final var two = new BlobKey(BlobKey.BlobType.FILE_DATA, 2);
		final var three = new BlobKey(BlobKey.BlobType.FILE_DATA, 2);
		final var twoRef = two;

		assertNotEquals(two, one);
		assertEquals(two, twoRef);
		assertEquals(two, three);
		assertNotEquals(one, null);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());
	}
}
