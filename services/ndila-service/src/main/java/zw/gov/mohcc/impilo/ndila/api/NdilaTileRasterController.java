package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ndila.core.tiles.NdilaTileRasterFacade;

@RestController
public class NdilaTileRasterController {

    private final NdilaTileRasterFacade rasterFacade;

    public NdilaTileRasterController(NdilaTileRasterFacade rasterFacade) {
        this.rasterFacade = rasterFacade;
    }

    @GetMapping({
            "/api/v1/ndila/tiles/{z}/{x}/{y}.png",
            "/api/v1/maps/tiles/{z}/{x}/{y}.png"
    })
    public ResponseEntity<byte[]> tile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) throws Exception {
        if (z < 0 || z > 19 || x < 0 || y < 0) {
            return ResponseEntity.badRequest().build();
        }
        byte[] png = rasterFacade.renderPng(z, x, y);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }
}
