package org.vcl.qasentinel.qa.controller;

import java.nio.file.Files;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.vcl.qasentinel.qa.model.BugReportListItem;
import org.vcl.qasentinel.qa.service.BugReportService;

@RestController
@RequestMapping("/api/v1/bugs")
@RequiredArgsConstructor
@Slf4j
public class BugReportController {

	private final BugReportService bugReportService;

	@GetMapping
	public List<BugReportListItem> list() {
		return bugReportService.listBugReports();
	}

	@GetMapping("/{jiraKey}/screenshot")
	public ResponseEntity<Resource> screenshot(@PathVariable("jiraKey") String jiraKey) {
		var path = bugReportService.resolveScreenshotFile(jiraKey);
		if (path.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		try {
			byte[] bytes = Files.readAllBytes(path.get());
			ByteArrayResource body = new ByteArrayResource(bytes);
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
					.body(body);
		}
		catch (Exception e) {
			log.warn("Could not read screenshot for {}: {}", jiraKey, e.getMessage());
			return ResponseEntity.internalServerError().build();
		}
	}
}
