package com.careerpilot.service;
import com.careerpilot.entity.InterviewQuestion;
import com.careerpilot.entity.JobApplication;
import com.careerpilot.entity.UserAccount;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DemoInterviewQuestionService implements InterviewQuestionService {
    public List<InterviewQuestion> generate(UserAccount u, JobApplication a, String difficulty) {
        String role = a.role;
        return List.of(
                new InterviewQuestion(u, a, "ROLE KNOWLEDGE", difficulty, "What would you prioritize in your first 30 days as a " + role + " at " + a.company + "?", "Connect the role's responsibilities with a concrete discovery plan.", "Context → priorities → collaborators → measurable checkpoints"),
                new InterviewQuestion(u, a, "PROJECT EXPERIENCE", difficulty, "Walk me through a project that best demonstrates your fit for this " + role + " role.", "Choose an example from your actual resume and explain your personal contribution.", "Situation → your responsibility → actions → verified outcome → reflection"),
                new InterviewQuestion(u, a, "PROBLEM SOLVING", difficulty, "Describe a difficult trade-off you made on a project and how you evaluated it.", "Use a real decision and explain the alternatives you considered.", "Goal → constraints → options → decision evidence → result"),
                new InterviewQuestion(u, a, "BEHAVIORAL", difficulty, "Tell me about a time you received feedback that changed how you worked.", "Share a truthful example and focus on what changed afterward.", "Situation → feedback → response → observable change"),
                new InterviewQuestion(u, a, "SCENARIO", difficulty, "How would you approach an ambiguous request from a stakeholder with a tight deadline?", "Explain how you clarify outcomes, surface risks, and communicate scope.", "Clarify → prioritize → propose options → confirm → deliver and review")
        );
    }
}
