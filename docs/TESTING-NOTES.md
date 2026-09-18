# Catatan Testing — NoteNest (branch `deploy/monolith`)

Unit testing dengan Mockito, satu test repository di H2, dan satu test end-to-end yang
menjalankan seluruh aplikasi. Ditulis untuk belajar + portfolio.

> Di `dev`, test tersebar di tiga modul dan dijalankan tiga kali. Di sini semuanya satu
> perintah: `cd backend && ./mvnw test`.

---

## Yang diuji, dan kenapa di situ

| Lapisan | Diuji? | Alasan |
|---|---|---|
| **Service** (`*ServiceImpl`) | ✅ Mockito | Tempat logika bisnis: kepemilikan, share, terjemahan error |
| **Repository** — `NoteRepository` saja | ✅ `@DataJpaTest` + H2 | Satu-satunya repository dengan JPQL buatan tangan |
| **Full context** (`@SpringBootTest`) | ✅ **baru di branch ini** | Perakitannya sendiri yang berisiko — lihat di bawah |
| Repository lain | ❌ | Hanya derived query (`findByUserId`, dst.) — Spring Data yang menulis SQL-nya |
| Controller (`MockMvc`) sendirian | ❌ | Sudah tercakup `ApiEndToEndTest` yang memanggilnya lewat HTTP |

**Kenapa `@SpringBootTest` sekarang ada, padahal di `dev` di-skip?**
Di `dev` alasan melewatkannya kuat: ia butuh PostgreSQL **dan** Eureka hidup, jadi gampang
gagal karena lingkungan, bukan karena kode. Di sini tidak ada Eureka, dan databasenya cukup
H2 — biayanya tinggal beberapa detik.

Dan justru di branch inilah test itu paling dibutuhkan. Merakit tiga aplikasi Spring jadi satu
menciptakan kelas kesalahan yang tidak bisa dilihat unit test: bean terdaftar dua kali, rantai
security yang tidak nyambung, filter yang tidak pernah terpasang. Semuanya hanya muncul saat
context benar-benar di-start.

Itu bukan teori. Dua bug nyata tertangkap justru oleh test ini saat branch dibangun:

| Bug | Gejala | Sebabnya |
|---|---|---|
| Auditing mati | Setiap insert gagal: `created_at` NULL | `@EnableJpaAuditing` di kelas config terpisah, sementara `@EnableJpaRepositories` ditulis eksplisit → EntityManagerFactory dibangun lebih dulu |
| Handler exception hilang | `404` muncul sebagai `500` | Pola exclude di pom ikut membuang `GlobalExceptionHandler` milik monolith sendiri |

Keduanya lolos kompilasi dan lolos semua unit test.

---

## Istilah inti (Mockito + JUnit 5)

| Istilah | Fungsi |
|---------|--------|
| **`@ExtendWith(MockitoExtension.class)`** | Aktifkan Mockito tanpa menyalakan Spring context → test jalan dalam milidetik |
| **`@Mock`** | Dependency palsu (`NoteRepository`, `UserClient`) yang bisa diatur mau mengembalikan apa |
| **`@InjectMocks`** | Suntik semua `@Mock` ke constructor class yang diuji (`NoteServiceImpl`) |
| **`when(...).thenReturn(...)`** | Atur skenario — termasuk `Optional.empty()` untuk "tidak ketemu" |
| **`verify(..., never())`** | Pastikan sesuatu **tidak** terjadi, mis. `save()` tidak dipanggil |
| **`ArgumentCaptor`** | Tangkap objek yang dikirim ke `save()` untuk diperiksa isinya |
| **`@DataJpaTest`** | Muat irisan JPA saja + database in-memory, tiap test di-rollback |
| **`@SpringBootTest` + `@AutoConfigureMockMvc`** | Muat seluruh context, panggil API lewat `MockMvc` tanpa membuka port |

**Pola tiap test (AAA — Arrange, Act, Assert)** dan nama `method_kondisi_hasil()`:
```java
@Test
void shareNote_byNonOwner_isForbiddenBeforeAnyUserLookup() {
    // Arrange: note milik orang lain
    when(noteRepository.findById(noteId)).thenReturn(Optional.of(note(noteId, UUID.randomUUID())));

    // Act + Assert: ditolak
    assertThrows(ForbiddenException.class,
            () -> noteService.shareNote(noteId, UUID.randomUUID(), request));

    // Assert: dan sisi user tidak pernah ditanya
    verify(userClient, never()).findByEmail(any());
}
```

---

## Bagian terpenting 1: batas note → user

Di `dev`, bagian ini soal Feign: yang membuktikan paham Feign bukan menulis `@FeignClient`,
tapi **apa yang terjadi saat panggilannya gagal**. `NoteServiceImpl` membedakan dua kegagalan:

| Kegagalan | Di `dev` | Di branch ini |
|---|---|---|
| Email tujuan tidak terdaftar | `FeignException.NotFound` → `UserNotFoundException` → `404` | `Optional.empty()` → `UserNotFoundException` → `404` |
| user-service mati / 5xx | `FeignException` dibiarkan lewat → `502` | **tidak ada** — tidak ada jaringan yang bisa putus |

Test `shareNote_userServiceUnreachable_propagatesFeignException` karena itu **dihapus**, bukan
diperbaiki. Menyimpannya dengan mock berarti menguji skenario yang tidak bisa terjadi.

Yang tersisa dan tetap penting: 404 harus tetap 404 dengan pesan yang berarti
(`shareNote_targetEmailNotRegistered_becomesUserNotFound`), dan `save()` tidak boleh dipanggil.

Batas domainnya sendiri tidak hilang. `UserClient` tetap di-mock seperti dulu — `NoteServiceImpl`
tidak tahu dan tidak peduli siapa yang mengisinya.

## Bagian terpenting 2: rantai security

`ApiEndToEndTest` menguji hal yang di `dev` dijaga proses terpisah (gateway), jadi tidak pernah
punya test:

| Test | Yang dibuktikan |
|---|---|
| `protectedEndpointRejectsRequestWithoutToken` | Tanpa token → `401`, bukan `200` |
| `protectedEndpointRejectsInvalidToken` | Token ngawur → `401`, bukan `500` |
| `tokenIsTranslatedIntoIdentityHeaders` | Klaim token jadi `X-User-Id` yang dibaca controller |
| `clientSuppliedIdentityHeadersAreIgnored` | Header `X-User-Id` kiriman client **dibuang** |

Yang terakhir itu paling berharga. Tanpa `HttpServletRequestWrapper` yang membuang header dari
luar, siapa pun bisa mengaku jadi user lain hanya dengan menambah satu header — dan tidak ada
satu pun unit test yang akan melihatnya.

---

## Gotcha

### 1. Mockito gagal mock class di JDK baru
```
Mockito cannot mock this class ... Could not modify all classes
```
`byte-buddy` bawaan Spring Boot 3.4.6 belum kenal bytecode JDK terbaru. Di `<properties>`:
```xml
<byte-buddy.version>1.18.12</byte-buddy.version>
```
Di `dev` baris ini harus diulang di enam `pom.xml`; di sini cukup satu.

### 2. `createdAt` null — dua sebab berbeda
Di `dev`: `@DataJpaTest` tidak men-scan `JpaConfig`, jadi auditing mati. Fix-nya
`@Import(JpaConfig.class)`.

Di sini **kebalikannya**. `@EnableJpaAuditing` menempel di `NoteNestApplication`, dan
`@DataJpaTest` memakai kelas itu sebagai akar context — jadi auditing sudah aktif. Meng-import
config auditing tambahan justru membuat bean `jpaAuditingHandler` terdaftar dua kali dan
seluruh context gagal start:
```
BeanDefinitionOverrideException: Invalid bean definition with name 'jpaAuditingHandler'
```

### 3. `@DataJpaTest` tidak boleh butuh infrastruktur yang tidak ada
`src/test/resources/application.properties` memakai H2 mode PostgreSQL. Di `dev` file ini juga
harus mematikan discovery (`eureka.client.enabled=false`), kalau tidak test mencoba mendaftar
ke Eureka yang tidak hidup. Di sini baris itu tidak perlu — tidak ada klien discovery di
classpath.

### 4. `jwt.secret` di test harus Base64 yang sah
`ApiEndToEndTest` men-start context sungguhan, dan `JwtAuthFilter` men-decode secret sebagai
Base64. Nilai seperti `"test-secret"` akan gagal saat request pertama, bukan saat startup —
jadi gejalanya muncul jauh dari penyebabnya.

---

## Ringkasan test

**36 test, semua lulus.**

| Kelas | Test | Yang dicek |
|---|---|---|
| **`AuthServiceImplTest`** (4) | `register_newEmail_savesHashedPasswordAndReturnsToken` | password di-hash, token dibuat |
| | `register_emailAlreadyTaken_throwsAndSavesNothing` | `EmailAlreadyExistsException`, `save()` tidak dipanggil |
| | `login_validCredentials_returnsTokenForThatUser` | `authenticate()` dipanggil, token untuk user yang benar |
| | `login_wrongPassword_propagatesBadCredentialsAndNeverIssuesToken` | `BadCredentialsException` diteruskan, token tidak dibuat |
| **`ProfileServiceImplTest`** (6) | `getMyProfile_firstAccess_createsProfileFromTokenClaims` | profil dibuat dari header, bukan dari body |
| | `getMyProfile_existingProfile_doesNotCreateAnother` | tidak ada profil ganda |
| | `updateMyProfile_overwritesEditableFieldsOnly` | displayName/bio berubah, `email` tetap |
| | `searchByEmail_excludesTheRequesterFromResults` | diri sendiri tidak muncul |
| | `searchByEmail_blankQuery_returnsEmptyWithoutHittingDatabase` | query kosong tidak menyentuh DB |
| | `getByEmail_unknownEmail_throwsProfileNotFound` | 404 yang nanti jadi `Optional.empty()` |
| **`NoteServiceImplTest`** (13) | `createNote_storesTagsLowercasedAndMarksResponseAsOwned` | normalisasi tag, `owned=true` |
| | `getNoteById_sharedWithRequester_isReadableButNotOwned` | penerima share boleh baca, `owned=false` |
| | `getNoteById_neitherOwnerNorShared_isForbidden` | `403` |
| | `getNoteById_unknownId_throwsNoteNotFound` | `404` |
| | `updateNote_byNonOwner_isForbiddenAndChangesNothing` | `403`, tidak ada `save()` |
| | `deleteNote_byNonOwner_isForbiddenAndDeletesNothing` | `403`, tidak ada `delete()` |
| | `shareNote_resolvesTargetEmailThroughUserClientAndSnapshotsIt` | `UserClient` dipanggil, email disnapshot |
| | `shareNote_targetEmailNotRegistered_becomesUserNotFound` | `Optional.empty()` → `UserNotFoundException` |
| | `shareNote_byNonOwner_isForbiddenBeforeAnyUserLookup` | cek pemilik sebelum lookup |
| | `shareNote_toSelf_isRejected` | `IllegalArgumentException` → `400` |
| | `shareNote_sameTargetTwice_returnsExistingShareInsteadOfDuplicating` | idempoten |
| | `getNotesSharedWithMe_looksUpEachOwnerOnlyOnce` | cache pemilik per request |
| | `revokeShare_whenNotShared_throwsNoteNotFound` | `404` |
| **`NoteRepositoryTest`** (5, H2) | `searchOwnedNotes_withoutFilters_returnsOnlyOwnNotesWithoutDuplicates` | `distinct` bekerja walau join ke tag |
| | `searchOwnedNotes_filterByTag_returnsOnlyMatchingNotes` | filter tag |
| | `searchOwnedNotes_freeTextMatchesTitleAndContentCaseInsensitively` | pencarian teks |
| | `searchOwnedNotes_supportsSortingAndPaging` | `Pageable` + `countQuery` |
| | `findNotesSharedWith_returnsNotesOwnedByOthersThatWereSharedToThisUser` | join share → note |
| **`ApiEndToEndTest`** (8, H2) | `contextLoads` | tidak ada bean ganda, seluruh perakitan bisa start |
| | `registerIsPublicAndReturnsToken` | path publik lolos filter, token terbit |
| | `protectedEndpointRejectsRequestWithoutToken` | `401` |
| | `protectedEndpointRejectsInvalidToken` | `401`, bukan `500` |
| | `tokenIsTranslatedIntoIdentityHeaders` | klaim token → `X-User-*` → controller |
| | `clientSuppliedIdentityHeadersAreIgnored` | anti-spoofing |
| | `shareResolvesTargetUserLocally` | alur share lengkap tanpa jaringan |
| | `shareToUnknownEmailReturnsNotFound` | `404`, bukan `500` |

Dibanding `dev` (29 test): `+8` dari `ApiEndToEndTest`, `−1` dari test Feign yang dihapus.

---

## Cara menjalankan

```bash
cd backend
./mvnw test                               # semua 36 test
./mvnw test -Dtest=NoteServiceImplTest    # satu class saja
./mvnw test -Dtest=ApiEndToEndTest        # hanya yang end-to-end
```

Tidak ada yang butuh Docker atau database hidup. Di `dev`, perintah ini harus diulang di tiga
folder service.
