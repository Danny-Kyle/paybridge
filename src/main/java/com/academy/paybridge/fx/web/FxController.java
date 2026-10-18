package com.academy.paybridge.fx.web;
import com.academy.paybridge.fx.api.FxApi;
import com.academy.paybridge.fx.service.FxService;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@org.springframework.context.annotation.Profile({"local","test"})
@RestController
@RequestMapping("/api/v1/fx/quotes")
@PreAuthorize("hasRole('CUSTOMER')")
public class FxController {
 private final FxService service;
 public FxController(FxService service){this.service=service;}
 public record Request(@Positive long sourceUsdMinor){}
 @PostMapping public FxApi.Quote quote(@Valid @RequestBody Request r){return service.quote(r.sourceUsdMinor());}
 @GetMapping("/{id}") public FxApi.Quote get(@PathVariable UUID id){return service.get(id);}
}
