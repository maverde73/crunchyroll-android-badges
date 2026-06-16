package com.maverde.crunchybadges

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the data-loading invariant behind [com.maverde.crunchybadges.ui.main.MainViewModel].
 *
 * The grid's "badges keep moving and never settle" bug came from the ViewModel
 * launching a NEW, never-cancelled collector on every tab switch / filter change
 * (`viewModelScope.launch { repository.getSeriesFiltered(filter).collect { ... } }`).
 * Every superseded query stayed alive and kept pushing its own ordering into the
 * shared list, so the RecyclerView animated back and forth between competing orders.
 *
 * The fix drives the list from a single `_currentFilter.flatMapLatest { ... }`
 * pipeline, which CANCELS the previous query the moment the filter changes.
 * These tests pin that behaviour: one models the (correct) flatMapLatest pipeline,
 * the other documents the original stacking bug it replaces.
 *
 * `fakeQuery` stands in for a Room query: each filter maps to a flow that emits a
 * single "ordering signature" after a 100ms latency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesListPipelineTest {

    private fun fakeQuery(filter: Int) = flow {
        delay(100)      // simulate DB query latency
        emit(filter)    // the ordering produced for this filter
    }

    @Test
    fun flatMapLatest_keeps_only_the_latest_filters_result() = runTest {
        val filter = MutableStateFlow(1)
        val emissions = mutableListOf<Int>()
        val job = launch {
            filter.flatMapLatest { fakeQuery(it) }.collect { emissions += it }
        }

        advanceTimeBy(50)   // query(1) in flight (needs 100ms)
        filter.value = 2    // supersede -> flatMapLatest cancels query(1)
        advanceTimeBy(50)   // query(2) in flight
        filter.value = 3    // supersede -> cancels query(2)
        advanceUntilIdle()  // only query(3) survives to completion
        job.cancel()

        // Single, final ordering reaches the grid -> no reshuffling.
        assertEquals(listOf(3), emissions)
    }

    @Test
    fun stacking_collectors_without_cancellation_lets_superseded_queries_emit() = runTest {
        // Reproduces the ORIGINAL bug: a collector launched per filter change,
        // none cancelled. Every query eventually completes and writes its order.
        val filter = MutableStateFlow(1)
        val emissions = mutableListOf<Int>()
        val jobs = mutableListOf<Job>()
        fun reload() { jobs += launch { fakeQuery(filter.value).collect { emissions += it } } }

        reload()                       // query(1) starts at t=0
        advanceTimeBy(50)
        filter.value = 2; reload()     // query(2) starts at t=50, query(1) NOT cancelled
        advanceTimeBy(50)
        filter.value = 3; reload()     // query(3) starts at t=100, 1 & 2 NOT cancelled
        advanceUntilIdle()
        jobs.forEach { it.cancel() }

        // All three orderings were delivered -> the grid reshuffled three times.
        assertEquals(listOf(1, 2, 3), emissions)
    }
}
