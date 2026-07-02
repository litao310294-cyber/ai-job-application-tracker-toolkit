package com.lt.aijobscreeningagent.profile;

import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

  private final UserProfileRepository userProfileRepository;

  public UserProfileService(UserProfileRepository userProfileRepository) {
    this.userProfileRepository = userProfileRepository;
  }

  public UserProfileResponse saveManualProfile(UserProfileRequest request) {
    UserProfile profile = userProfileRepository.saveDefault(request);
    return UserProfileResponse.from(profile);
  }

  public UserProfileResponse getCurrentProfile() {
    return userProfileRepository.findDefault()
        .map(UserProfileResponse::from)
        .orElseGet(UserProfileResponse::empty);
  }
}
