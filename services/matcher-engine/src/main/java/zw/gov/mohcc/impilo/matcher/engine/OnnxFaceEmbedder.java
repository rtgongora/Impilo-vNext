package zw.gov.mohcc.impilo.matcher.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * ONNX face-embedding inference. Loads a face-recognition model (ArcFace /
 * MobileFaceNet style: input NCHW 1×3×112×112, output an embedding vector) from
 * {@code matcher.face.model-path}. When the path is unset or the model is absent,
 * {@link #ready()} is false and the {@link FaceMatcherImpl} extract path fails
 * closed — the engine never fabricates an embedding.
 *
 * <p>The model is an EXTERNAL_INTEGRATION artifact (fetched by
 * {@code scripts/dev/vendor-biometric-deps.sh} into a mounted path, never
 * committed). Matching itself (cosine over embeddings) needs no model and is
 * always real.</p>
 */
@Component
public class OnnxFaceEmbedder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxFaceEmbedder.class);
    private static final int SIZE = 112; // typical ArcFace input edge

    private final int inputSize;
    private volatile OrtEnvironment env;
    private volatile OrtSession session;
    private volatile String inputName;

    public OnnxFaceEmbedder(@Value("${matcher.face.model-path:}") String modelPath,
                            @Value("${matcher.face.input-size:112}") int inputSize) {
        this.inputSize = inputSize > 0 ? inputSize : SIZE;
        if (modelPath == null || modelPath.isBlank()) {
            log.info("Face model path unset — face extraction fails closed until a model is mounted.");
            return;
        }
        Path p = Path.of(modelPath);
        if (!Files.isReadable(p)) {
            log.warn("Face model not readable at {} — face extraction fails closed.", modelPath);
            return;
        }
        try {
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
            this.inputName = session.getInputNames().iterator().next();
            log.info("Face embedding model loaded from {} (input '{}')", modelPath, inputName);
        } catch (Exception e) {
            log.error("Failed to load face model {} — face extraction fails closed: {}", modelPath, e.getMessage());
            this.session = null;
        }
    }

    public boolean ready() {
        return session != null;
    }

    /** Decode an image, preprocess to NCHW [1,3,H,W] in [-1,1], run inference, return the embedding. */
    public float[] embed(byte[] imageBytes) {
        if (!ready()) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                return null;
            }
            float[] chw = preprocess(img);
            long[] shape = {1, 3, inputSize, inputSize};
            try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
                 OrtSession.Result result = session.run(Map.of(inputName, input))) {
                Object out = result.get(0).getValue();
                return flatten(out);
            }
        } catch (Exception e) {
            log.warn("Face embedding inference failed: {}", e.getMessage());
            return null;
        }
    }

    private float[] preprocess(BufferedImage src) {
        Image scaled = src.getScaledInstance(inputSize, inputSize, Image.SCALE_SMOOTH);
        BufferedImage rgb = new BufferedImage(inputSize, inputSize, BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(scaled, 0, 0, null);
        float[] chw = new float[3 * inputSize * inputSize];
        int hw = inputSize * inputSize;
        for (int y = 0; y < inputSize; y++) {
            for (int x = 0; x < inputSize; x++) {
                int p = rgb.getRGB(x, y);
                int idx = y * inputSize + x;
                // scale to [-1,1] (ArcFace convention: (v-127.5)/128)
                chw[idx] = (((p >> 16) & 0xFF) - 127.5f) / 128f;          // R
                chw[hw + idx] = (((p >> 8) & 0xFF) - 127.5f) / 128f;      // G
                chw[2 * hw + idx] = ((p & 0xFF) - 127.5f) / 128f;         // B
            }
        }
        return chw;
    }

    @SuppressWarnings("unchecked")
    private float[] flatten(Object out) {
        if (out instanceof float[] f) {
            return f;
        }
        if (out instanceof float[][] f2 && f2.length > 0) {
            return f2[0];
        }
        return null;
    }

    @Override
    public void close() {
        try {
            if (session != null) session.close();
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
