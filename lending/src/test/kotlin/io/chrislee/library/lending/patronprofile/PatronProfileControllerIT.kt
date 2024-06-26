package io.chrislee.library.lending.patronprofile

import arrow.core.None
import arrow.core.left
import arrow.core.right
import arrow.core.some
import com.ninjasquad.springmockk.MockkBean
import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.mockTransactionalOperator
import io.chrislee.library.lending.LendingConfiguration
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldFailedEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvents
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patron.application.usecase.CancelHoldUseCase
import io.chrislee.library.lending.patron.application.usecase.PlacingOnHoldUseCase
import io.chrislee.library.lending.patronprofile.adapter.PatronProfileConfiguration
import io.chrislee.library.lending.patronprofile.adapter.web.PatronProfileController
import io.chrislee.library.lending.patronprofile.adapter.web.PlaceHoldRequest
import io.chrislee.library.lending.patronprofile.application.domain.Checkout
import io.chrislee.library.lending.patronprofile.application.domain.CheckoutsView
import io.chrislee.library.lending.patronprofile.application.domain.Hold
import io.chrislee.library.lending.patronprofile.application.domain.HoldsView
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfile
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.time.Instant

@WebFluxTest(LendingConfiguration::class)
@AutoConfigureWebTestClient
@ContextConfiguration(
    classes = [
        PatronProfileController::class,
        PatronProfileConfiguration::class,
        PatronProfileControllerConfiguration::class,
    ],
)
class PatronProfileControllerIT {
    @Autowired
    private lateinit var client: WebTestClient

    @MockkBean
    private lateinit var patronProifileRepository: PatronProfileRepository

    @MockkBean
    private lateinit var placingOnHoldUseCase: PlacingOnHoldUseCase

    @MockkBean
    private lateinit var cancelHoldUseCase: CancelHoldUseCase

    @MockkBean
    private lateinit var domainEventPublisher: DomainEventPublisher

    private val patronId = PatronId.uniqueOne()
    private val bookId = BookId.uniqueOne()
    private val anotherBookId = BookId.uniqueOne()
    private val then = Instant.now()
    private val thenPlusHundredSeconds = then.plusSeconds(100)

    private fun profile(): PatronProfile {
        return PatronProfile(
            holdsView = HoldsView(mapOf(bookId to Hold(bookId, then.some()))),
            checkoutsView = CheckoutsView(mapOf(anotherBookId to Checkout(anotherBookId, thenPlusHundredSeconds))),
        )
    }

    @DisplayName("고객 프로파일 정보를 조회할 수 있어야 한다")
    @Test
    fun shouldContainPatronProfileResource() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            client.get()
                .uri("/profiles/${patronId.source}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.body.holdsView").isArray
                .jsonPath("$.body.holdsView[0].bookId").isEqualTo(bookId.source)
                .jsonPath("$.body.holdsView[0].till").isEqualTo(then.toString())
                .jsonPath("$.body.checkoutsView").isArray
                .jsonPath("$.body.checkoutsView[0].bookId").isEqualTo(anotherBookId.source)
                .jsonPath("$.body.checkoutsView[0].till").isEqualTo(thenPlusHundredSeconds.toString())
        }
    }

    @DisplayName("대여 예약 목록을 조회할 수 있어야 한다")
    @Test
    fun shouldContainHoldsViewResource() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            client.get()
                .uri("/profiles/${patronId.source}/holds")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.body.[0].bookId").isEqualTo(bookId.source)
                .jsonPath("$.body.[0].till").isEqualTo(then.toString())
        }
    }

    @DisplayName("대여 목록을 조회할 수 있어야 한다")
    @Test
    fun shouldContainCheckoutsViewResource() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            client.get()
                .uri("/profiles/${patronId.source}/checkouts")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.body.[0].bookId").isEqualTo(anotherBookId.source)
                .jsonPath("$.body.[0].till").isEqualTo(thenPlusHundredSeconds.toString())
        }
    }

    @DisplayName("대여 예약 단건 조회 시 없다면 404를 반환해야 한다")
    @Test
    fun shouldReturn404WhenThereIsNoHold() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            client.get()
                .uri("/profiles/${patronId.source}/holds/${BookId.uniqueOne().source}")
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @DisplayName("대여 예약 단건을 조회할 수 있어야 한다")
    @Test
    fun shouldReturnResourceForHold() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            client.get()
                .uri("/profiles/${patronId.source}/holds/${bookId.source}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.body.bookId").isEqualTo(bookId.source)
                .jsonPath("$.body.till").isEqualTo(then.toString())
        }
    }

    @DisplayName("대여 단건 조회 시 없다면 404를 반환해야 한다")
    @Test
    fun shouldReturn404WhenThereIsNoCheckout() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            client.get()
                .uri("/profiles/${patronId.source}/checkouts/${BookId.uniqueOne().source}")
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @DisplayName("대여 단건을 조회할 수 있어야 한다")
    @Test
    fun shouldReturnResourceForCheckout() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            client.get()
                .uri("/profiles/${patronId.source}/checkouts/${anotherBookId.source}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.body.bookId").isEqualTo(anotherBookId.source)
                .jsonPath("$.body.till").isEqualTo(thenPlusHundredSeconds.toString())
        }
    }

    @DisplayName("대여 예약을 할 수 있어야 한다")
    @Test
    fun shouldPlaceBookOnHold() {
        runTest {
            val events = mockk<BookPlacedOnHoldEvents>()
            every { events.bookPlacedOnHoldEvent } returns mockk<BookPlacedOnHoldEvent>()
            every { events.maximumNumberOnHoldsReachedEvent } returns None
            coEvery { placingOnHoldUseCase.execute(any()) } returns events.right()
            coEvery { domainEventPublisher.publish(any()) } returns Unit.right()
            client.post()
                .uri("/profiles/${patronId.source}/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    Mono.just(PlaceHoldRequest(bookId = bookId.source, numberOfDays = 10)),
                    PlaceHoldRequest::class.java,
                )
                .exchange()
                .expectStatus().isOk
        }
    }

    @DisplayName("대여 예약 유스케이스 오류로 실패한다면 대여 예약 시 500을 반환해야 한다")
    @Test
    fun shouldReturn500IfPlacingOnHoldUseCaseRaisesError() {
        runTest {
            coEvery { placingOnHoldUseCase.execute(any()) } returns mockk<BookHoldFailedEvent>().left()
            coEvery { domainEventPublisher.publish(any()) } returns Unit.right()
            client.post()
                .uri("/profiles/${patronId.source}/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    Mono.just(PlaceHoldRequest(bookId = bookId.source, numberOfDays = 10)),
                    PlaceHoldRequest::class.java,
                )
                .exchange()
                .expectStatus().is5xxServerError
        }
    }

    @DisplayName("도메인 이벤트 발행 시스템 오류로 실패한다면 대여 예약 시 500을 반환해야 한다")
    @Test
    fun shouldReturn500IfDomainEventPublisherRaisesErrorWhilePlacingOnHold() {
        runTest {
            val events = mockk<BookPlacedOnHoldEvents>()
            every { events.bookPlacedOnHoldEvent } returns mockk<BookPlacedOnHoldEvent>()
            every { events.maximumNumberOnHoldsReachedEvent } returns None
            coEvery { placingOnHoldUseCase.execute(any()) } returns events.right()
            coEvery { domainEventPublisher.publish(any()) } returns SystemError.IOError("io error").left()
            client.post()
                .uri("/profiles/${patronId.source}/holds")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    Mono.just(PlaceHoldRequest(bookId = bookId.source, numberOfDays = 10)),
                    PlaceHoldRequest::class.java,
                )
                .exchange()
                .expectStatus().is5xxServerError
        }
    }

    @DisplayName("대여 예약을 취소할 수 있어야 한다")
    @Test
    fun shouldCancelExistingHold() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            coEvery { cancelHoldUseCase.execute(any()) } returns mockk<BookHoldCancelledEvent>().right()
            coEvery { domainEventPublisher.publish(any()) } returns Unit.right()
            client.delete()
                .uri("/profiles/${patronId.source}/holds/${bookId.source}")
                .exchange()
                .expectStatus().isOk
        }
    }

    @DisplayName("해당 도서에 대한 예약을 하지 않은 경우 대여 예약을 취소할 수 없다")
    @Test
    fun shouldNotCancelNotExistingHold() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            val event = mockk<BookHoldCancellingFailedEvent>()
            every { event.reason } returns BookHoldCancellingFailedEvent.Reason.BookNotHeld
            coEvery { cancelHoldUseCase.execute(any()) } returns event.left()
            coEvery { domainEventPublisher.publish(any()) } returns Unit.right()
            client.delete()
                .uri("/profiles/${patronId.source}/holds/${bookId.source}")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @DisplayName("도서 예약 취소 유스케이스에서 오류가 발생할 경우 500을 반환해야 한다")
    @Test
    fun shouldReturn500IfCancelHoldUseCaesRaisesError() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            val event = mockk<BookHoldCancellingFailedEvent>()
            every { event.reason } returns BookHoldCancellingFailedEvent.Reason.System
            coEvery { cancelHoldUseCase.execute(any()) } returns event.left()
            coEvery { domainEventPublisher.publish(any()) } returns Unit.right()
            client.delete()
                .uri("/profiles/${patronId.source}/holds/${bookId.source}")
                .exchange()
                .expectStatus().is5xxServerError
        }
    }

    @DisplayName("도메인 이벤트 발행 시스템 오류가 발생할 경우 500을 반환해야 한다")
    @Test
    fun shouldReturn500IfDomainEventPublisherRaisesErrorWhileCancelling() {
        runTest {
            coEvery { patronProifileRepository.findByPatronId(patronId) } returns profile().right()
            coEvery { cancelHoldUseCase.execute(any()) } returns mockk<BookHoldCancelledEvent>().right()
            coEvery { domainEventPublisher.publish(any()) } returns SystemError.IOError("io error").left()
            client.delete()
                .uri("/profiles/${patronId.source}/holds/${bookId.source}")
                .exchange()
                .expectStatus().is5xxServerError
        }
    }
}

@TestConfiguration
private class PatronProfileControllerConfiguration {
    @Bean
    fun transactionalOperator(): TransactionalOperator {
        return mockTransactionalOperator().first
    }
}
