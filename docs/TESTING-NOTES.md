# Catatan Testing — NoteNest

Catatan unit testing dengan Mockito, ditambah satu test repository di H2. Ditulis untuk belajar + portfolio.

---

## Yang diuji, dan kenapa di situ

| Lapisan | Diuji? | Alasan |
|---|---|---|
| **Service** (`*ServiceImpl`) | ✅ Mockito | Tempat logika bisnis: kepemilikan, share, terjemahan error Feign |
| **Repository** — `NoteRepository` saja | ✅ `@DataJpaTest` + H2 | Satu-satunya repository dengan JPQL buatan tangan |
| Repository lain | ❌ | Hanya derived query (`findByUserId`, dst.) — Spring Data yang menulis SQL-nya |
| Controller (`MockMvc`) | ❌ | Controller hanya meneruskan header + body ke service |
| Full context (`@SpringBootTest`) | ❌ | Butuh PostgreSQL dan Eureka hidup; mudah gagal karena lingkungan, bukan kode |

**Beda dengan ShopNest:** ShopNest melewatkan test repository sama sekali. NoteNest menambahkannya karena `searchOwnedNotes` bukan derived query — satu query melayani tiga kombinasi filter, memakai `left join` ke koleksi tag, dan butuh `countQuery` terpisah. Salah join di sana tidak akan ketahuan oleh test bermock, karena mock tidak pernah menjalankan query.

---

## Istilah inti (Mockito + JUnit 5)

| Istilah | Fungsi |
|---------|--------|
| **`@ExtendWith(MockitoExtension.class)`** | Aktifkan Mockito tanpa menyalakan Spring context → test jalan dalam milidetik |
| **`@Mock`** | Dependency palsu (`NoteRepository`, `UserClient`) yang bisa diatur mau mengembalikan apa |
| **`@InjectMocks`** | Suntik semua `@Mock` ke constructor class yang diuji (`NoteServiceImpl`) |
| **`when(...).thenReturn(...)`** | Atur skenario normal |
| **`when(...).thenThrow(...)`** | Atur skenario error — dipakai untuk mensimulasikan user-service membalas 404 atau mati |
| **`verify(..., never())`** | Pastikan sesuatu **tidak** terjadi, mis. `save()` tidak dipanggil atau Feign tidak disentuh |
| **`ArgumentCaptor`** | Tangkap objek yang dikirim ke `save()` untuk diperiksa isinya |
| **`@DataJpaTest`** | Muat irisan JPA saja + database in-memory, tiap test di-rollback |

**Pola tiap test (AAA — Arrange, Act, Assert)** dan nama `method_kondisi_hasil()`:
```java
@Test
void shareNote_byNonOwner_isForbiddenBeforeAnyFeignCall() {
    // Arrange: note milik orang lain
    when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, UUID.randomUUID())));

    // Act + Assert: ditolak
    assertThrows(ForbiddenException.class,
            () -> noteService.shareNote(noteId, UUID.randomUUID(), request));

    // Assert: dan user-service tidak pernah ditanya
    verify(userClient, never()).getUserByEmail(any());
}
```

---

## Bagian terpenting: test error Feign

Menulis `@FeignClient` itu cuma annotation. Yang membuktikan paham Feign adalah **apa yang terjadi saat panggilannya gagal**. `NoteServiceImpl` membedakan dua jenis kegagalan, dan keduanya diuji:

| Kegagalan | Cara disimulasikan | Yang diharapkan | Status ke client |
|---|---|---|---|
| Email tujuan tidak terdaftar | `thenThrow(mock(FeignException.NotFound.class))` | Diterjemahkan jadi `UserNotFoundException` berisi email-nya | `404` |
| user-service mati / 5xx | `thenThrow(mock(FeignException.ServiceUnavailable.class))` | `FeignException` dibiarkan lewat apa adanya | `502` (oleh `GlobalExceptionHandler`) |

Kenapa dibedakan: `404` adalah **kesalahan pemakai** (salah ketik email) dan pantas dijawab dengan pesan yang jelas. Service mati adalah **masalah infrastruktur** — kalau ikut diterjemahkan jadi `404`, client akan mengira email-nya salah padahal bukan.

Di kedua kasus test juga memastikan `noteShareRepository.save()` tidak pernah dipanggil.

`FeignException.NotFound` dibuat pakai `mock(...)` karena constructor-nya butuh objek `Request` Feign yang merepotkan; mock cukup karena yang diperiksa hanya **tipe** exception-nya.

---

## Gotcha

### 1. Mockito gagal mock class di JDK baru
```
Mockito cannot mock this class ... Could not modify all classes
```
`byte-buddy` bawaan Spring Boot 3.4.6 belum kenal bytecode JDK terbaru. Fix yang sama dengan ShopNest, di `<properties>` tiap `pom.xml`:
```xml
<byte-buddy.version>1.18.12</byte-buddy.version>
```

### 2. `createdAt` null di `@DataJpaTest`
`@DataJpaTest` hanya memuat irisan JPA, jadi `JpaConfig` (`@EnableJpaAuditing`) tidak ikut ter-scan dan auditing mati. `findNotesSharedWith` mengurutkan berdasarkan `createdAt`, jadi ini bukan kosmetik. Fix: `@Import(JpaConfig.class)` di class test.

### 3. Test repository tidak boleh butuh Eureka
`src/test/resources/application.properties` di note-service mematikan discovery (`eureka.client.enabled=false`) dan memakai H2 mode PostgreSQL. Tanpa itu, test mencoba mendaftar ke Eureka yang tidak ada.

---

## Ringkasan test per service

**29 test, semua lulus** (terakhir dijalankan 2026-09-17).

| Service | Test | Yang dicek |
|---------|------|-------------|
| **auth-service** (4) | `register_newEmail_savesHashedPasswordAndReturnsToken` | password di-hash, token dibuat |
| | `register_emailAlreadyTaken_throwsAndSavesNothing` | `EmailAlreadyExistsException`, `save()` tidak dipanggil |
| | `login_validCredentials_returnsTokenForThatUser` | `authenticate()` dipanggil, token untuk user yang benar |
| | `login_wrongPassword_propagatesBadCredentialsAndNeverIssuesToken` | `BadCredentialsException` diteruskan, token tidak dibuat |
| **user-service** (6) | `getMyProfile_firstAccess_createsProfileFromTokenClaims` | profil dibuat dari header, bukan dari body |
| | `getMyProfile_existingProfile_doesNotCreateAnother` | tidak ada profil ganda |
| | `updateMyProfile_overwritesEditableFieldsOnly` | displayName/bio berubah, `email` tetap |
| | `searchByEmail_excludesTheRequesterFromResults` | diri sendiri tidak muncul |
| | `searchByEmail_blankQuery_returnsEmptyWithoutHittingDatabase` | query kosong tidak menyentuh DB |
| | `getByEmail_unknownEmail_throwsProfileNotFound` | 404 yang nanti diterima Feign |
| **note-service** (14) | `createNote_storesTagsLowercasedAndMarksResponseAsOwned` | normalisasi tag, `owned=true` |
| | `getNoteById_sharedWithRequester_isReadableButNotOwned` | penerima share boleh baca, `owned=false` |
| | `getNoteById_neitherOwnerNorShared_isForbidden` | `403` |
| | `getNoteById_unknownId_throwsNoteNotFound` | `404` |
| | `updateNote_byNonOwner_isForbiddenAndChangesNothing` | `403`, tidak ada `save()` |
| | `deleteNote_byNonOwner_isForbiddenAndDeletesNothing` | `403`, tidak ada `delete()` |
| | `shareNote_resolvesTargetEmailThroughFeignAndSnapshotsIt` | Feign dipanggil, email disnapshot |
| | `shareNote_targetEmailNotRegistered_translatesFeign404ToUserNotFound` | Feign 404 → `UserNotFoundException` |
| | `shareNote_userServiceUnreachable_propagatesFeignException` | Feign 503 diteruskan → `502` |
| | `shareNote_byNonOwner_isForbiddenBeforeAnyFeignCall` | cek pemilik sebelum Feign |
| | `shareNote_toSelf_isRejected` | `IllegalArgumentException` → `400` |
| | `shareNote_sameTargetTwice_returnsExistingShareInsteadOfDuplicating` | idempoten |
| | `getNotesSharedWithMe_looksUpEachOwnerOnlyOnce` | cache pemilik per request |
| | `revokeShare_whenNotShared_throwsNoteNotFound` | `404` |
| **NoteRepository** (5, H2) | `searchOwnedNotes_withoutFilters_returnsOnlyOwnNotesWithoutDuplicates` | `distinct` bekerja walau join ke tag |
| | `searchOwnedNotes_filterByTag_returnsOnlyMatchingNotes` | filter tag |
| | `searchOwnedNotes_freeTextMatchesTitleAndContentCaseInsensitively` | pencarian teks |
| | `searchOwnedNotes_supportsSortingAndPaging` | `Pageable` + `countQuery` |
| | `findNotesSharedWith_returnsNotesOwnedByOthersThatWereSharedToThisUser` | join share → note |

---

## Cara menjalankan

```bash
cd backend/note-service
./mvnw test                               # semua test di service itu
./mvnw test -Dtest=NoteServiceImplTest    # satu class saja
```

Ulangi di `backend/auth-service` dan `backend/user-service`. Tidak ada yang butuh Docker atau database hidup.
