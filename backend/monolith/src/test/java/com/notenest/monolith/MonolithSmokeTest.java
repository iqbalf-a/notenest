package com.notenest.monolith;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Yang diuji di sini bukan logika bisnis - itu sudah punya test sendiri di
// masing-masing service. Yang diuji adalah hal-hal yang HANYA bisa salah karena
// penggabungan: tiga context jadi satu, JwtAuthFilter pindah dari reaktif ke
// servlet, dan Feign diganti panggilan method langsung.
@SpringBootTest
@AutoConfigureMockMvc
class MonolithSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Kalau penggabungan menyisakan bean ganda (@EnableJpaAuditing tiga kali,
    // dua SecurityFilterChain, @RestControllerAdvice yang berebut tipe exception
    // yang sama), kegagalannya muncul di sini - sebelum test lain sempat jalan.
    @Test
    void contextLoads() {
    }

    @Test
    void registerIsPublicAndReturnsToken() throws Exception {
        String body = register("pendaftar@notenest.test", "Pendaftar");
        assertTrue(json(body).path("data").path("accessToken").asText().length() > 20,
                "register harus mengembalikan accessToken");
    }

    // Dulu dijaga gateway. Sekarang JwtAuthFilter versi servlet yang menjaganya,
    // dan inilah buktinya masih tertutup.
    @Test
    void protectedEndpointRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void protectedEndpointRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/notes").header("Authorization", "Bearer bukan-token-sungguhan"))
                .andExpect(status().isUnauthorized());
    }

    // Membuktikan rantai lengkapnya: filter membaca klaim token, memasang
    // X-User-Id, dan NoteController membacanya lewat @RequestHeader - persis
    // kontrak yang dulu dipenuhi gateway.
    @Test
    void tokenIsTranslatedIntoIdentityHeaders() throws Exception {
        String token = tokenOf(register("pemilik@notenest.test", "Pemilik"));

        mockMvc.perform(get("/api/notes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // Header X-User-* kiriman client harus dibuang, bukan dipercaya. Tanpa ini
    // siapa pun bisa mengaku jadi user lain hanya dengan menambah satu header.
    @Test
    void clientSuppliedIdentityHeadersAreIgnored() throws Exception {
        String token = tokenOf(register("asli@notenest.test", "Asli"));
        String korban = json(register("korban@notenest.test", "Korban")).path("data").path("userId").asText();

        // Bikin satu note sebagai "asli", lalu coba baca daftar note sambil
        // mengaku sebagai "korban" lewat header palsu.
        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Catatan Asli\",\"content\":\"isi\"}"))
                .andExpect(status().isCreated());

        String listed = mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", korban))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Header palsu diabaikan -> yang terbaca tetap note milik "asli"
        assertEquals(1, json(listed).path("data").path("content").size());
        assertEquals("Catatan Asli", json(listed).path("data").path("content").get(0).path("title").asText());
    }

    // Inilah jalur yang dulu memakai Feign lewat Eureka. Sekarang UserClient
    // lokal memanggil ProfileService langsung; hasilnya harus tetap sama.
    @Test
    void shareResolvesTargetUserWithoutFeign() throws Exception {
        String ownerToken = tokenOf(register("owner@notenest.test", "Owner"));
        String readerToken = tokenOf(register("reader@notenest.test", "Reader"));

        // Profil dibuat saat pertama kali diakses, bukan saat register (lihat
        // ProfileServiceImpl.findOrCreate). Jadi keduanya harus menyentuh
        // /api/users/me dulu: reader supaya bisa dicari lewat email saat share,
        // owner supaya namanya bisa ditampilkan di daftar shared-with-me.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        String created = mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dibagikan\",\"content\":\"isi\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String noteId = json(created).path("data").path("id").asText();

        mockMvc.perform(post("/api/notes/" + noteId + "/share")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetEmail\":\"reader@notenest.test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sharedWithEmail").value("reader@notenest.test"));

        mockMvc.perform(get("/api/notes/shared-with-me").header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Dibagikan"))
                .andExpect(jsonPath("$.data[0].ownerEmail").value("owner@notenest.test"));
    }

    // Email yang tidak terdaftar dulu jadi FeignException.NotFound, sekarang
    // UserNotFoundException. Yang dilihat client harus tetap 404, bukan 500.
    @Test
    void shareToUnknownEmailReturnsNotFound() throws Exception {
        String ownerToken = tokenOf(register("solo@notenest.test", "Solo"));

        String created = mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sendiri\",\"content\":\"isi\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String noteId = json(created).path("data").path("id").asText();

        mockMvc.perform(post("/api/notes/" + noteId + "/share")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetEmail\":\"tidak-ada@notenest.test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ===== helper =====

    private String register(String email, String displayName) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + displayName + "\",\"email\":\"" + email
                                + "\",\"password\":\"rahasia123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String tokenOf(String registerResponse) throws Exception {
        return json(registerResponse).path("data").path("accessToken").asText();
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
