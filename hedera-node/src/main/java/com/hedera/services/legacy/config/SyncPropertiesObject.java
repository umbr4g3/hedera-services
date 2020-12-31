package com.hedera.services.legacy.config;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.logic.CustomProperties;

/**
 * This class reads and stores values from property file which should be Synchronous
 * i.e. the value, which are assumed to be refreshed once handleTrsnaction method is invoked and 
 * they need to be same in all Nodes, if these values are not same , it will result in ISS exception
 * In other words, they impact state of system.
 */
public class SyncPropertiesObject {
	// Estimates for calculating fees for Smart Contract local calls
	private static int localCallEstReturnBytes = ApplicationConstants.LOCAL_CALLEST_RET_BYTES;
	private static int maxGasLimit = ApplicationConstants.MAX_GAS_LIMIT;

	public static void loadSynchProperties(CustomProperties appConfig) {
		// Estimates for calculating fees for Smart Contract local calls
		localCallEstReturnBytes = appConfig.getInt("localCallEstReturnBytes", ApplicationConstants.LOCAL_CALLEST_RET_BYTES);

		maxGasLimit = appConfig.getInt("maxGasLimit", ApplicationConstants.MAX_GAS_LIMIT);
	}

	static int getLocalCallEstReturnBytes() {
		return localCallEstReturnBytes;
	}

	static int getMaxGasLimit() {
	    return maxGasLimit;
	}
}
