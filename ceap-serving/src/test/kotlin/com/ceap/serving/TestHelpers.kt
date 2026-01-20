package com.ceap.serving

import com.ceap.model.Candidate
import com.ceap.storage.CandidateRepository
import com.ceap.storage.StorageException

/**
 * In-memory implementation of CandidateRepository for testing.
 */
class InMemoryCandidateRepository : CandidateRepository {
    private val storage = mutableMapOf<String, Candidate>()
    
    private fun makeKey(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ): String {
        return "$customerId#$programId#$marketplaceId#$subjectType#$subjectId"
    }
    
    override fun create(candidate: Candidate): Candidate {
        val programContext = candidate.context.find { it.type == "program" }
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
        
        if (programContext == null || marketplaceContext == null) {
            throw StorageException("Candidate must have program and marketplace context")
        }
        
        val key = makeKey(
            candidate.customerId,
            programContext.id,
            marketplaceContext.id,
            candidate.subject.type,
            candidate.subject.id
        )
        storage[key] = candidate
        return candidate
    }
    
    override fun get(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ): Candidate? {
        val key = makeKey(customerId, programId, marketplaceId, subjectType, subjectId)
        return storage[key]
    }
    
    override fun update(candidate: Candidate): Candidate {
        val programContext = candidate.context.find { it.type == "program" }
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
        
        if (programContext == null || marketplaceContext == null) {
            throw StorageException("Candidate must have program and marketplace context")
        }
        
        val key = makeKey(
            candidate.customerId,
            programContext.id,
            marketplaceContext.id,
            candidate.subject.type,
            candidate.subject.id
        )
        storage[key] = candidate
        return candidate
    }
    
    override fun delete(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ) {
        val key = makeKey(customerId, programId, marketplaceId, subjectType, subjectId)
        storage.remove(key)
    }
    
    override fun batchWrite(candidates: List<Candidate>) = throw NotImplementedError()
    override fun queryByProgramAndChannel(programId: String, channelId: String, limit: Int, ascending: Boolean) = throw NotImplementedError()
    override fun queryByProgramAndDate(programId: String, date: String, limit: Int) = throw NotImplementedError()
}

/**
 * Simple ranking strategy for testing.
 */
class SimpleRankingStrategy : RankingStrategy {
    override fun rank(candidates: List<Candidate>, channel: String?, customerId: String): List<Candidate> {
        return candidates
    }
}
