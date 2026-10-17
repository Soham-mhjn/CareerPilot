package com.careerpilot.service;
import com.careerpilot.entity.InterviewQuestion;
import com.careerpilot.entity.JobApplication;
import com.careerpilot.entity.UserAccount;

import java.util.List;

public interface InterviewQuestionService {
    List<InterviewQuestion> generate(UserAccount owner, JobApplication application, String difficulty);
}
