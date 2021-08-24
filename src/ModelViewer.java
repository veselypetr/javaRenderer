import lwjglutils.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import transforms.*;

import java.io.IOException;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Semestralní projekt PGRF2
 * Zadání A1
 * <p>
 * Založeno na lwjgl-samples.
 * <p>
 * Model je převzat z https://www.turbosquid.com/3d-models/piper-pa-18-supercub-fbx-free/1041070,
 * jeho tvůrcem je uživatel lucas_pl, a je poskytnut pod royalty-free licencí.
 * Program je založený na lwjgl-samples lvl1basic/p02geometry/p03obj
 */

public class ModelViewer {

    int width, height;
    double ox, oy;
    private boolean mouseButton1 = false;
    private boolean pause = false;
    private boolean vsync = false;

    // The window handle
    private long window;

    OGLModelOBJ model;
    OGLBuffers modelBuffers;
    OGLTexture2D modelTexture;
    OGLModelOBJ prop;

    OGLModelOBJ skybox;
    OGLBuffers skyboxBuffers;
    OGLTexture2D skyboxTexture;

    OGLTextRenderer textRenderer;
    int locMat, locMV, locHeight;
    int skyboxShader, teapotShaderProgram, piperShader, lightPosition;
    double lastTime;
    double currentTime, tick, tick2 = 0;
    Mat4 animateBody;
    Mat4 propRot;
    Camera cam = new Camera();
    Mat4 propToRoot, rootToProp, proj, swapYZ = new Mat4(new double[]{
            1, 0, 0, 0,
            0, 0, 1, 0,
            0, 1, 0, 0,
            0, 0, 0, 1,
    });

    boolean textured = true;

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(1280, 720, "Semestrální projekt A2 PGRF2 - Petr Veselý, 5. 2. 2020", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                switch (key) {
                    case GLFW_KEY_W:
                        cam = cam.forward(1);
                        break;
                    case GLFW_KEY_D:
                        cam = cam.right(1);
                        break;
                    case GLFW_KEY_S:
                        cam = cam.backward(1);
                        break;
                    case GLFW_KEY_A:
                        cam = cam.left(1);
                        break;
                    case GLFW_KEY_LEFT_CONTROL:
                        cam = cam.down(1);
                        break;
                    case GLFW_KEY_LEFT_SHIFT:
                        cam = cam.up(1);
                        break;
                    case GLFW_KEY_SPACE:
                        cam = cam.withFirstPerson(!cam.getFirstPerson());
                        break;
                    case GLFW_KEY_P:
                        pause = !pause;
                        break;
                    case GLFW_KEY_F:
                        textured = !textured;
                        break;
                    case GLFW_KEY_C:
                        vsync = !vsync;
                        break;
                    case GLFW_KEY_ESCAPE:
                        glfwSetWindowShouldClose(window, true);
                        break;
                }
            }
        });

        glfwSetCursorPosCallback(window, new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double x, double y) {
                if (mouseButton1) {
                    cam = cam.addAzimuth((double) Math.PI * (ox - x) / width)
                            .addZenith((double) Math.PI * (oy - y) / width);
                    ox = x;
                    oy = y;
                }
            }
        });

        glfwSetMouseButtonCallback(window, new GLFWMouseButtonCallback() {

            @Override
            public void invoke(long window, int button, int action, int mods) {
                mouseButton1 = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS;

                if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS) {
                    mouseButton1 = true;
                    DoubleBuffer xBuffer = BufferUtils.createDoubleBuffer(1);
                    DoubleBuffer yBuffer = BufferUtils.createDoubleBuffer(1);
                    glfwGetCursorPos(window, xBuffer, yBuffer);
                    ox = xBuffer.get(0);
                    oy = yBuffer.get(0);
                }

                if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_RELEASE) {
                    mouseButton1 = false;
                    DoubleBuffer xBuffer = BufferUtils.createDoubleBuffer(1);
                    DoubleBuffer yBuffer = BufferUtils.createDoubleBuffer(1);
                    glfwGetCursorPos(window, xBuffer, yBuffer);
                    double x = xBuffer.get(0);
                    double y = yBuffer.get(0);
                    cam = cam.addAzimuth((double) Math.PI * (ox - x) / width)
                            .addZenith((double) Math.PI * (oy - y) / width);
                    ox = x;
                    oy = y;
                }
            }

        });

        glfwSetFramebufferSizeCallback(window, new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int w, int h) {
                if (w > 0 && h > 0 &&
                        (w != width || h != height)) {
                    width = w;
                    height = h;
                    proj = new Mat4PerspRH(Math.PI / 4, height / (double) width, 0.01, 1000.0);
                    if (textRenderer != null)
                        textRenderer.resize(width, height);
                }
            }
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }
        glfwMakeContextCurrent(window);

        glfwSwapInterval(1);

        glfwShowWindow(window);

        GL.createCapabilities();

        teapotShaderProgram = ShaderUtils.loadProgram("/shaders/teapot");
        piperShader = ShaderUtils.loadProgram("/shaders/piper");
        skyboxShader = ShaderUtils.loadProgram("/shaders/sky");

        model = new OGLModelOBJ("/obj/piper_crushed_noprop.obj");
        prop = new OGLModelOBJ("/obj/piper_crushed_justprop.obj");
        skybox = new OGLModelOBJ("/obj/skybox2.obj");

        propToRoot = new Mat4Transl(new Vec3D(0, -3.125, -1.235));
        rootToProp = new Mat4Transl(new Vec3D(0, +3.125, +1.235));
        tick = 0;
        tick2 = 0;

        modelBuffers = model.getBuffers();
        skyboxBuffers = skybox.getBuffers();

        glClearColor(0.2f, 0.2f, 0.2f, 1.0f);

        glUseProgram(this.piperShader);

        locMV = glGetUniformLocation(piperShader, "mv");
        lightPosition = glGetUniformLocation(piperShader, "lightPosition");

        glUniform3f(lightPosition, -5, 1, 5);


        try {
            System.out.println("LOADING TEXTURES");
            modelTexture = new OGLTexture2D("textures/piper_diffuse2.jpg");
            skyboxTexture = new OGLTexture2D("textures/skybox2.jpg");
        } catch (IOException e) {
            e.printStackTrace();
        }

        cam = cam.withPosition(new Vec3D(6, 6, 3))
                .withAzimuth(Math.PI * 1.25)
                .withZenith(Math.PI * -0.085);

        glEnable(GL_DEPTH_TEST);
        lastTime = glfwGetTime();
        textRenderer = new OGLTextRenderer(width, height);
    }


    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            if (vsync) {
                glfwSwapInterval(2);
            } else {
                glfwSwapInterval(1);
            }

            lastTime = currentTime;
            currentTime = glfwGetTime();
            double frametimeScalar = (currentTime - lastTime) / 0.01667;

            if (pause) {
                frametimeScalar = 0;
            }
            tick = (tick + (1 * frametimeScalar)) % 360;
            propRot = animateProp(tick);
            animateBody = animateBody(tick, frametimeScalar);

            String text = new String("[LMB] camera, [WSAD] to move, [L-Shift], [L-Ctrl] for camera up/down, [F] to swap shaders, [P] to pause animations, [C] to change sync interval.");
            String attribution = new String("Zadání A2, Petr Veselý, PGRF2, 5.2.2020");

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glUniform1f(locHeight, height);

            glViewport(0, 0, width, height);

            modelTexture.bind(piperShader, "piper_diffuse2.png", 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);


            if (textured) {
                glUseProgram(piperShader);

                locMat = glGetUniformLocation(piperShader, "mat");
                locHeight = glGetUniformLocation(piperShader, "height");

                glUniformMatrix4fv(locMat, false, ToFloatArray.convert(swapYZ.mul(animateBody).mul(cam.getViewMatrix()).mul(proj)));
                glUniformMatrix4fv(locMV, false, ToFloatArray.convert(swapYZ));
                modelBuffers.draw(model.getTopology(), piperShader);

                glUniformMatrix4fv(locMat, false, ToFloatArray.convert(swapYZ.mul(propToRoot).mul(propRot).mul(rootToProp).mul(animateBody).mul(cam.getViewMatrix()).mul(proj)));
                glUniformMatrix4fv(locMV, false, ToFloatArray.convert(swapYZ.mul(propRot)));
                prop.getBuffers().draw(model.getTopology(), piperShader);
            } else {
                glUseProgram(teapotShaderProgram);

                locMat = glGetUniformLocation(teapotShaderProgram, "mat");
                locHeight = glGetUniformLocation(teapotShaderProgram, "height");

                glUniformMatrix4fv(locMat, false, ToFloatArray.convert(swapYZ.mul(animateBody).mul(cam.getViewMatrix()).mul(proj)));
                modelBuffers.draw(model.getTopology(), teapotShaderProgram);
                glUniformMatrix4fv(locMat, false, ToFloatArray.convert(swapYZ.mul(propToRoot).mul(propRot).mul(rootToProp).mul(animateBody).mul(cam.getViewMatrix()).mul(proj)));
                prop.getBuffers().draw(model.getTopology(), teapotShaderProgram);
            }

            glUseProgram(skyboxShader);

            locMat = glGetUniformLocation(skyboxShader, "mat");
            locHeight = glGetUniformLocation(skyboxShader, "height");
            glUniformMatrix4fv(locMat, false, ToFloatArray.convert(swapYZ.mul(new Mat4Scale(500)).mul(cam.getViewMatrix()).mul(proj)));

            skyboxTexture.bind(skyboxShader, "skybox", 0);
            skyboxBuffers.draw(model.getTopology(), skyboxShader);


            textRenderer.clear();
            textRenderer.addStr2D(3, 20, text);
            textRenderer.addStr2D(width - 225, height - 5, attribution);
            textRenderer.draw();


            glfwSwapBuffers(window);

            glfwPollEvents();
        }
    }

    public void run() {
        init();
        loop();
    }

    private Mat4 animateProp(double tick) {
        return new Mat4RotY(tick / 2);
    }

    private Mat4 animateBody(double tick, double frametimeScalar) {
        if (tick < 90 || (tick > 270 && tick < 360)) {
            tick2 = tick2 + (1 * frametimeScalar);
        }
        if (tick > 90 && tick < 270) {
            tick2 = tick2 -  (1 * frametimeScalar);
        }
        return new Mat4RotY((double) tick2 / 3600);
    }

}