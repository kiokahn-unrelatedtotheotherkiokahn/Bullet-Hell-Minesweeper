package com.MindSweeper;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class Main implements ApplicationListener {

    // Board settings
    private final int ROWS = 21;
    private final int COLS = 21;
    private final int TILE_SIZE = 50;
    private final int MINES = 10;
    private float timer = 0;

    private final float WORLD_WIDTH = COLS * TILE_SIZE;
    private final float WORLD_HEIGHT = ROWS * TILE_SIZE;

    // Player settings
    private final float PLAYER_SIZE = 24;

    // Graphics
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;
    private OrthographicCamera camera;

    // Mouse position
    private final Vector3 mousePosition = new Vector3();

    // Minefield
    private boolean[][] mines;
    private boolean[][] revealed;
    private boolean[][] flagged;

    // Player
    private Vector2 player;

    // Game state
    private boolean gameOver;
    private boolean won;
    private int revealedCount;

    @Override
    public void create() {

        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();

        font.getData().setScale(1.2f);

        camera = new OrthographicCamera();
        player = new Vector2();

        updateCamera(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );

        newGame();
    }

    private void updateCamera(int width, int height) {

        if (width <= 0 || height <= 0) {
            return;
        }

        float aspect = (float) width / height;

        float viewWidth;
        float viewHeight;

        if (aspect >= 1) {
            viewHeight = WORLD_HEIGHT;
            viewWidth = WORLD_HEIGHT * aspect;
        } else {
            viewWidth = WORLD_WIDTH;
            viewHeight = WORLD_WIDTH / aspect;
        }

        camera.setToOrtho(false, viewWidth, viewHeight);

        camera.position.set(
            WORLD_WIDTH / 2f,
            WORLD_HEIGHT / 2f,
            0
        );

        camera.update();
    }

    // --------------------------------
    // NEW GAME
    // --------------------------------

    private void newGame() {

        mines = new boolean[ROWS][COLS];
        revealed = new boolean[ROWS][COLS];
        flagged = new boolean[ROWS][COLS];

        // Start in the exact center of the center tile.
        int centerRow = ROWS / 2;
        int centerCol = COLS / 2;

        player.set(
            (centerCol + 0.5f) * TILE_SIZE,
            (centerRow + 0.5f) * TILE_SIZE
        );
        timer = 0;
        gameOver = false;
        won = false;
        revealedCount = 0;

        // Place mines outside the diamond-shaped safe zone.
        int placedMines = 0;

        while (placedMines < MINES) {

            int row = MathUtils.random(0, ROWS - 1);
            int col = MathUtils.random(0, COLS - 1);

            if (isInSafeStar(row, col, centerRow, centerCol)) {
                continue;
            }

            if (!mines[row][col]) {
                mines[row][col] = true;
                placedMines++;
            }
        }
    }

    // --------------------------------
    // DIAMOND-SHAPED SAFE ZONE
    // --------------------------------

    private boolean isInSafeStar(
        int row,
        int col,
        int centerRow,
        int centerCol
    ) {

        int rowDistance = Math.abs(row - centerRow);
        int colDistance = Math.abs(col - centerCol);

        return rowDistance + colDistance <= 2;
    }

    // --------------------------------
    // RENDER
    // --------------------------------

    @Override
    public void render() {

        float delta = Math.min(
            Gdx.graphics.getDeltaTime(),
            0.05f
        );

        Gdx.gl.glClearColor(
            0.08f, 0.08f, 0.11f, 1
        );

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!gameOver && !won) {
            timer += delta;
            handleMovement(delta);

            // Handle clicks before automatically revealing.
            handleMouseInput();

            // Automatically reveal the tile under the player.
            checkPlayerTile();
        }

        camera.update();

        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        drawBoard();
        drawPlayer();
        drawUI();

        // Restart after winning or losing.
        if ((gameOver || won)
            && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {

            newGame();
        }
    }

    // --------------------------------
    // PLAYER MOVEMENT
    // --------------------------------

    private void handleMovement(float delta) {

        float moveX = 0;
        float moveY = 0;

        if (Gdx.input.isKeyPressed(Input.Keys.W)
            || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            moveY++;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.S)
            || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            moveY--;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.A)
            || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            moveX--;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.D)
            || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            moveX++;
        }

        Vector2 movement = new Vector2(moveX, moveY);

        // Prevent diagonal movement from being faster.
        if (movement.len() > 0) {
            float PLAYER_SPEED = 300;
            movement.nor().scl(PLAYER_SPEED * delta);
            player.add(movement);
        }

        // Keep player inside the board.
        player.x = MathUtils.clamp(
            player.x,
            PLAYER_SIZE / 2,
            WORLD_WIDTH - PLAYER_SIZE / 2
        );

        player.y = MathUtils.clamp(
            player.y,
            PLAYER_SIZE / 2,
            WORLD_HEIGHT - PLAYER_SIZE / 2
        );
    }

    // --------------------------------
    // MOUSE CONTROLS
    // --------------------------------

    private void handleMouseInput() {

        mousePosition.set(
            Gdx.input.getX(),
            Gdx.input.getY(),
            0
        );

        camera.unproject(mousePosition);

        int col = (int)(mousePosition.x / TILE_SIZE);
        int row = (int)(mousePosition.y / TILE_SIZE);

        if (row < 0 || row >= ROWS
            || col < 0 || col >= COLS) {
            return;
        }

        // RIGHT CLICK = FLAG
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {

            if (!revealed[row][col]) {
                flagged[row][col] = !flagged[row][col];
            }
        }

        // LEFT CLICK = REVEAL
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {

            if (!flagged[row][col]) {
                revealArea(row, col);
            }
        }
    }

    // --------------------------------
    // AUTOMATIC REVEAL
    // --------------------------------

    private void checkPlayerTile() {

        int col = (int)(player.x / TILE_SIZE);
        int row = (int)(player.y / TILE_SIZE);

        if (row < 0 || row >= ROWS
            || col < 0 || col >= COLS) {
            return;
        }

        // Don't reveal flagged tiles.
        if (flagged[row][col]) {
            return;
        }

        if (!revealed[row][col]) {
            revealArea(row, col);
        }
    }

    // --------------------------------
    // MINESWEEPER REVEALING
    // --------------------------------

    private void revealArea(int row, int col) {

        // Check boundaries.
        if (row < 0 || row >= ROWS
            || col < 0 || col >= COLS) {
            return;
        }

        // Don't reveal already revealed or flagged tiles.
        if (revealed[row][col] || flagged[row][col]) {
            return;
        }

        // Check for mine.
        if (mines[row][col]) {

            gameOver = true;

            // Reveal all mines.
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (mines[r][c]) {
                        revealed[r][c] = true;
                    }
                }
            }

            return;
        }

        // Reveal safe tile.
        revealed[row][col] = true;
        revealedCount++;

        int nearby = countNearbyMines(row, col);

        // Automatically reveal surrounding tiles
        // when there are no nearby mines.
        if (nearby == 0) {

            for (int r = -1; r <= 1; r++) {
                for (int c = -1; c <= 1; c++) {

                    if (r == 0 && c == 0) {
                        continue;
                    }

                    revealArea(row + r, col + c);
                }
            }
        }

        // Check win condition.
        if (revealedCount == ROWS * COLS - MINES) {
            won = true;
        }
    }

    // --------------------------------
    // COUNT NEARBY MINES
    // --------------------------------

    private int countNearbyMines(int row, int col) {

        int count = 0;

        for (int r = -1; r <= 1; r++) {
            for (int c = -1; c <= 1; c++) {

                int checkRow = row + r;
                int checkCol = col + c;

                if (checkRow >= 0 && checkRow < ROWS
                    && checkCol >= 0 && checkCol < COLS) {

                    if (mines[checkRow][checkCol]) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    // --------------------------------
    // NUMBER COLORS
    // --------------------------------

    private Color getNumberColor(int number) {

        switch (number) {

            case 1:
                return Color.BLUE;

            case 2:
                return Color.GREEN;

            case 3:
                return Color.RED;

            case 4:
                return Color.PURPLE;

            case 5:
                return Color.ORANGE;

            default:
                return Color.BLACK;
        }
    }

    // --------------------------------
    // DRAW BOARD
    // --------------------------------

    private void drawBoard() {

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {

                float x = col * TILE_SIZE;
                float y = row * TILE_SIZE;

                if (revealed[row][col]) {

                    if (mines[row][col]) {
                        shapeRenderer.setColor(Color.RED);
                    } else {
                        shapeRenderer.setColor(
                            0.55f, 0.55f, 0.58f, 1
                        );
                    }

                } else if (flagged[row][col]) {

                    shapeRenderer.setColor(
                        0.1f, 0.6f, 0.2f, 1
                    );

                } else {

                    shapeRenderer.setColor(
                        0.22f, 0.28f, 0.36f, 1
                    );
                }

                shapeRenderer.rect(
                    x + 1,
                    y + 1,
                    TILE_SIZE - 2,
                    TILE_SIZE - 2
                );
            }
        }

        shapeRenderer.end();

        // Draw numbers, flags, and mines.
        batch.begin();

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {

                float x = col * TILE_SIZE + 20;
                float y = row * TILE_SIZE + 32;

                if (revealed[row][col]) {

                    if (mines[row][col]) {

                        font.setColor(Color.WHITE);
                        font.draw(batch, "M", x, y);

                    } else {

                        int nearby = countNearbyMines(row, col);

                        if (nearby > 0) {

                            font.setColor(getNumberColor(nearby));

                            font.draw(
                                batch,
                                String.valueOf(nearby),
                                x,
                                y
                            );
                        }
                    }

                } else if (flagged[row][col]) {

                    font.setColor(Color.WHITE);
                    font.draw(batch, "F", x, y);
                }
            }
        }

        batch.end();
    }

    // --------------------------------
    // DRAW PLAYER
    // --------------------------------

    private void drawPlayer() {

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(Color.GOLD);

        shapeRenderer.circle(
            player.x,
            player.y,
            PLAYER_SIZE / 2
        );

        shapeRenderer.end();
    }

    // --------------------------------
    // DRAW UI
    // --------------------------------

    private void drawUI() {

        batch.begin();

        font.setColor(Color.WHITE);

        font.draw(
            batch,
            "MINDSWEEPER",
            10,
            WORLD_HEIGHT - 20
        );

        font.draw(
            batch,
            "Revealed: " + revealedCount,
            10,
            WORLD_HEIGHT - 45
        );

        font.draw(
            batch,
            "Flags: " + countFlags(),
            10,
            WORLD_HEIGHT - 70
        );

        font.draw(
            batch,
            "WASD: Move | Right Click: Flag",
            10,
            WORLD_HEIGHT - 95
        );
        font.draw(batch, "Time: " + (int) timer, 5, WORLD_HEIGHT - 115);
        if (gameOver) {

            font.setColor(Color.RED);

            font.draw(
                batch,
                "GAME OVER! Press SPACE to restart",
                350,
                WORLD_HEIGHT / 2
            );

        } else if (won) {

            font.setColor(Color.GREEN);

            font.draw(
                batch,
                "YOU WIN! Press SPACE to restart",
                350,
                WORLD_HEIGHT / 2
            );
            font.draw(batch, "Final Time: " + (int) timer, WORLD_WIDTH/2 - 70, WORLD_HEIGHT/2 - 30);
        }

        batch.end();
    }

    // --------------------------------
    // COUNT FLAGS
    // --------------------------------

    private int countFlags() {

        int count = 0;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {

                if (flagged[row][col]) {
                    count++;
                }
            }
        }

        return count;
    }

    // --------------------------------
    // LIBGDX METHODS
    // --------------------------------

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            updateCamera(width, height);
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {

        shapeRenderer.dispose();
        batch.dispose();
        font.dispose();
    }
}
