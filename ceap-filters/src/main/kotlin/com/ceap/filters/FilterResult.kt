package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.RejectionRecord

/**
 * Result of filtering a candidate.
 * 
 * @property candidate The candidate that was filtered
 * @property passed Whether the candidate passed the filter
 * @property rejectionRecord Optional rejection record if the candidate was rejected
 */
data class FilterResult(
    val candidate: Candidate,
    val passed: Boolean,
    val rejectionRecord: RejectionRecord? = null
) {
    init {
        require(passed || rejectionRecord != null) {
            "Rejected candidates must have a rejection record"
        }
    }
    
    companion object {
        /**
         * Create a passing filter result.
         */
        fun pass(candidate: Candidate): FilterResult {
            return FilterResult(candidate, true, null)
        }
        
        /**
         * Create a failing filter result with rejection record.
         */
        fun reject(candidate: Candidate, rejectionRecord: RejectionRecord): FilterResult {
            return FilterResult(candidate, false, rejectionRecord)
        }
    }
}
