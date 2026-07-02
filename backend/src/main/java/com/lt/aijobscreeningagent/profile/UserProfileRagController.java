package com.lt.aijobscreeningagent.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class UserProfileRagController {

  private final UserProfileRagService userProfileRagService;

  public UserProfileRagController(UserProfileRagService userProfileRagService) {
    this.userProfileRagService = userProfileRagService;
  }

  @PostMapping("/reindex")
  public ProfileReindexResponse reindex(
      @RequestParam(required = false, defaultValue = "false") boolean includeHistory
  ) {
    return userProfileRagService.reindexDefaultProfile(includeHistory);
  }

  @GetMapping("/search")
  public ProfileSearchResponse search(
      @RequestParam String query,
      @RequestParam(required = false) Integer topK
  ) {
    return userProfileRagService.searchDefaultProfile(query, topK);
  }
}
