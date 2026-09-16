package com.notenest.userservice.service.impl;

import com.notenest.userservice.dto.request.UpdateProfileRequest;
import com.notenest.userservice.dto.response.ProfileResponse;
import com.notenest.userservice.dto.response.UserSummaryResponse;
import com.notenest.userservice.entity.Profile;
import com.notenest.userservice.exception.ProfileNotFoundException;
import com.notenest.userservice.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileServiceImpl profileService;

    private Profile profile(UUID userId, String email, String displayName) {
        return Profile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .email(email)
                .displayName(displayName)
                .build();
    }

    @Test
    void getMyProfile_firstAccess_createsProfileFromTokenClaims() {
        UUID userId = UUID.randomUUID();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.getMyProfile(userId, "iqbal@example.com", "Iqbal");

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(saved.capture());

        assertEquals(userId, saved.getValue().getUserId());
        assertEquals("iqbal@example.com", saved.getValue().getEmail());
        assertEquals("Iqbal", saved.getValue().getDisplayName());
        assertEquals("iqbal@example.com", response.getEmail());
    }

    @Test
    void getMyProfile_existingProfile_doesNotCreateAnother() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findByUserId(userId))
                .thenReturn(Optional.of(profile(userId, "iqbal@example.com", "Iqbal")));

        ProfileResponse response = profileService.getMyProfile(userId, "iqbal@example.com", "Iqbal");

        assertEquals("Iqbal", response.getDisplayName());
        verify(profileRepository, never()).save(any(Profile.class));
    }

    @Test
    void updateMyProfile_overwritesEditableFieldsOnly() {
        UUID userId = UUID.randomUUID();
        Profile existing = profile(userId, "iqbal@example.com", "Iqbal");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Iqbal Firman");
        request.setBio("Backend enthusiast");
        request.setAvatarUrl("https://example.com/a.png");

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateMyProfile(userId, "iqbal@example.com", "Iqbal", request);

        assertEquals("Iqbal Firman", response.getDisplayName());
        assertEquals("Backend enthusiast", response.getBio());
        // email berasal dari token, bukan dari body - update tidak boleh mengubahnya
        assertEquals("iqbal@example.com", response.getEmail());
    }

    @Test
    void searchByEmail_excludesTheRequesterFromResults() {
        UUID requesterId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        when(profileRepository.findTop10ByEmailContainingIgnoreCaseOrderByEmailAsc("example"))
                .thenReturn(List.of(
                        profile(requesterId, "iqbal@example.com", "Iqbal"),
                        profile(otherId, "budi@example.com", "Budi")));

        List<UserSummaryResponse> results = profileService.searchByEmail(requesterId, "example");

        assertEquals(1, results.size());
        assertEquals("budi@example.com", results.get(0).getEmail());
    }

    @Test
    void searchByEmail_blankQuery_returnsEmptyWithoutHittingDatabase() {
        assertTrue(profileService.searchByEmail(UUID.randomUUID(), "  ").isEmpty());
        verify(profileRepository, never()).findTop10ByEmailContainingIgnoreCaseOrderByEmailAsc(any());
    }

    @Test
    void getByEmail_unknownEmail_throwsProfileNotFound() {
        when(profileRepository.findByEmailIgnoreCase("hantu@example.com")).thenReturn(Optional.empty());

        assertThrows(ProfileNotFoundException.class, () -> profileService.getByEmail("hantu@example.com"));
    }
}
