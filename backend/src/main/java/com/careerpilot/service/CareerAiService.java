package com.careerpilot.service;
import com.careerpilot.entity.InterviewQuestion;
import com.careerpilot.entity.JobApplication;
import com.careerpilot.entity.UserAccount;

import java.util.List;
import java.util.Map;

public interface CareerAiService {
    Map<String, Object> analyze(String resumeText, String jobDescription);
    List<InterviewQuestion> generateQuestions(UserAccount owner, JobApplication application, String difficulty);
    Map<String, Object> careerInsights(Map<String, Object> metrics);
    String providerMode();
    boolean providerConfigured();
}
