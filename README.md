# Library project

## About

This is a Kotlin port of [Library project from ddd-by-examples](https://github.com/ddd-by-examples/library). Everything
about DDD and business logics in this project is indebted to that project. We use techniques connected with Hexagonal
Architecture with DDD, EDA, Functional Programming.

Most of the noteworthy design is in the lending service, so we recommend the domain package in the lending submodule
when you first start looking at the code.

## Getting Started

```bash
./gradlew clean build bootBuildImage
docker-compose up -d
```

If you want to see distributed tracing result, open your browser and go to:
http://localhost:9411/zipkin

## Multi-Module Hexagonal Architecture

Both catalog and lending are service submodules and packaged by bootJar.

common-xxx are common submodules packaged by simple jar and are shared by catalog and lending.

common-event specifies common protobuf events produced and consumed by core services.

```
└── library
    ├── common-domain
    ├── common-event
    ├── common-infrastructure
    ├── catalogue
    │   └── adapter
    │   └── application
    │       └── domain
    └── lending
        ├── book
        │   ├── adapter
        │   └── application
        │       ├── domain
        │       └── usecase
        ├── dailysheet
        │   ├── adapter
        │   └── application
        │       ├── domain
        │       └── usecase
        ├── patron
        │   ├── adapter
        │   └── application
        │       ├── domain
        │       └── usecase
        └── patronprofile
            ├── adapter
            └── application
                ├── domain
                └── usecase
```

## Functional Approach

We strongly use Either monad in [arrow-kt](https://arrow-kt.io/) for
[railway-oriented programming](https://fsharpforfunandprofit.com/rop/). For example, look at `PlacingOnHoldUseCase`
interface and `PatronProfileController` which uses that interface.

```kotlin
import arrow.core.getOrElse
import arrow.core.raise.either

interface PlacingOnHoldUseCase {
    suspend fun execute(command: PlacingOnHoldCommand): Either<BookHoldFailedEvent, BookPlacedOnHoldEvent>
}

@RestController
class PatronProfileController(
    private val placingOnHoldUseCase: PlacingOnHoldUseCase,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @PostMapping("/profiles/{patronId}/holds")
    suspend fun placeHold(
        @PathVariable patronId: String,
        @RequestBody request: PlaceHoldRequest,
    ): ResponseEntity<Any> {
        val command = command(patronId, request).getOrElse { return badRequest() }
        return either {
            val events = placingOnHoldUseCase.execute(command).bind()
            domainEventPublisher.publish(events.bookPlacedOnHoldEvent).bind()
            events.maximumNumberOnHoldsReachedEvent.onSome(domainEventPublisher::publish)
        }
            .fold(
                ifLeft = { ResponseEntity.internalServerError().build() },
                ifRight = { ResponseEntity.ok().build() },
            )
    }
}
```

`PatronProfileController` uses `PlacingOnHoldUseCase::execute` and bind returned value (using monadic bind
with `either {}` and `bind()` DSL) when result is Left.

This approach starts with domain types, especially with their smart constructor. Domain types with smart constructor
prevent data inconsistency, and error raised by smart constructor moves on rails(Either monadic chain).

```kotlin
import arrow.core.raise.either
import arrow.core.raise.ensure

@JvmInline
value class BookId private constructor(val source: String) {
    companion object {
        fun from(source: String): Either<InvalidStateError, BookId> {
            return either {
                ensure(source.trim().isNotBlank()) { InvalidStateError("도서 ID가 비어 있습니다") }
                BookId(source.trim())
            }
        }
    }
}

val eitherBookIdOrError = BookId.from("  ")
assert(eitherBookIdOrError.isLeft())
```

## Event-Driven Architecture

Interaction with two aggregates(book, patron) in lending service is driven by `DomainEventPublisher`, which is
implemented simply by `ApplicationEventPublisher`. Events in lending service are so-called inner events.
We can easily substitute the implementation that uses `ApplicationEventPublisher`  with which can also store events.

`Patron` type is basically immutable, and when a patron do `something()`, he returns an event without any state changes.
`PatronTransformer` transform `Patron(state1)` to `Patron(state2)` with that event. Changed patron state is stored
at database and we can publish of that event.

```kotlin
val patron = patronRepository.findByPatronId(patronId).bind()
val book = bookRepository.findAvailableBook(bookId).bind()
val holdDuration = HoldDuration.OpenEnded(Instant.now())
val event = patron.placeOnHold(book, holdDuration) // Patron do something and returns event
if (event is BookPlacedOnHoldEvent) {
    val changedPatron = PatronTransformer.transform(patron, event) // Patron changed due to event
    patronRepository.save(changedPatron).bind()
}
domainEventPublisher.publish(event).bind()
```

Catalog service publish events to be passed to external services(lending). `BookInstanceAddedToCatalogue` protobuf
message is defined at `common-event` submodule and catalog/lending services use that protobuf message. Protobuf message
is encoded into ByteArray by catalog service, is sent to Kafka MQ, and is consumed by lending service.
