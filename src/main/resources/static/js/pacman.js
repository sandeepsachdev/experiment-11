(() => {
    const TILE = 16;
    const COLS = 28;
    const ROWS = 31;

    // Maze layout: # wall, . dot, o power pellet, space empty, - ghost door
    const MAZE_TEMPLATE = [
        "############################",
        "#............##............#",
        "#.####.#####.##.#####.####.#",
        "#o####.#####.##.#####.####o#",
        "#.####.#####.##.#####.####.#",
        "#..........................#",
        "#.####.##.########.##.####.#",
        "#.####.##.########.##.####.#",
        "#......##....##....##......#",
        "######.##### ## #####.######",
        "     #.##### ## #####.#     ",
        "     #.##          ##.#     ",
        "     #.## ###--### ##.#     ",
        "######.## #      # ##.######",
        "      .   #      #   .      ",
        "######.## #      # ##.######",
        "     #.## ######## ##.#     ",
        "     #.##          ##.#     ",
        "     #.## ######## ##.#     ",
        "######.## ######## ##.######",
        "#............##............#",
        "#.####.#####.##.#####.####.#",
        "#.####.#####.##.#####.####.#",
        "#o..##................##..o#",
        "###.##.##.########.##.##.###",
        "###.##.##.########.##.##.###",
        "#......##....##....##......#",
        "#.##########.##.##########.#",
        "#.##########.##.##########.#",
        "#..........................#",
        "############################"
    ];

    const canvas = document.getElementById('game');
    const ctx = canvas.getContext('2d');
    const scoreEl = document.getElementById('score');
    const livesEl = document.getElementById('lives');
    const messageEl = document.getElementById('message');

    let maze, score, lives, dotsRemaining, pacman, ghosts, frightenedTimer, gameState;
    let lastTime = 0;

    function resetMaze() {
        maze = MAZE_TEMPLATE.map(row => row.split(''));
        dotsRemaining = 0;
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const ch = maze[r][c];
                if (ch === '.' || ch === 'o') dotsRemaining++;
            }
        }
    }

    function isWall(c, r) {
        if (r < 0 || r >= ROWS) return true;
        // tunnel wrapping handled by caller
        if (c < 0 || c >= COLS) return false;
        const ch = maze[r][c];
        return ch === '#';
    }

    function isGhostDoor(c, r) {
        if (r < 0 || r >= ROWS || c < 0 || c >= COLS) return false;
        return maze[r][c] === '-';
    }

    function newPacman() {
        return {
            x: 13.5 * TILE,
            y: 23 * TILE,
            dir: { x: 0, y: 0 },
            nextDir: { x: 0, y: 0 },
            speed: 80, // pixels per second
            mouth: 0,
            dead: false
        };
    }

    function newGhost(col, row, color, behavior) {
        return {
            x: col * TILE,
            y: row * TILE,
            startX: col * TILE,
            startY: row * TILE,
            dir: { x: -1, y: 0 },
            color: color,
            behavior: behavior,
            speed: 70,
            frightened: false,
            eaten: false
        };
    }

    function resetEntities() {
        pacman = newPacman();
        ghosts = [
            newGhost(13, 14, '#ff0000', 'chase'),     // Blinky
            newGhost(13, 14, '#ffb8ff', 'ambush'),    // Pinky
            newGhost(14, 14, '#00ffff', 'patrol'),    // Inky
            newGhost(14, 14, '#ffb852', 'random')     // Clyde
        ];
        frightenedTimer = 0;
    }

    function init() {
        resetMaze();
        resetEntities();
        score = 0;
        lives = 3;
        gameState = 'playing';
        messageEl.textContent = '';
        updateHud();
    }

    function updateHud() {
        scoreEl.textContent = score;
        livesEl.textContent = lives;
    }

    const DIRS = {
        ArrowUp:    { x: 0, y: -1 },
        ArrowDown:  { x: 0, y: 1 },
        ArrowLeft:  { x: -1, y: 0 },
        ArrowRight: { x: 1, y: 0 }
    };

    document.addEventListener('keydown', (e) => {
        if (DIRS[e.key]) {
            pacman.nextDir = DIRS[e.key];
            e.preventDefault();
        }
        if (e.key === 'r' || e.key === 'R') {
            init();
        }
    });

    function tileAt(px, py) {
        return { c: Math.floor(px / TILE), r: Math.floor(py / TILE) };
    }

    function centerOfTile(px, py) {
        const { c, r } = tileAt(px, py);
        return {
            x: c * TILE + TILE / 2,
            y: r * TILE + TILE / 2
        };
    }

    function canMove(px, py, dir, allowDoor) {
        // Check the tile the entity would enter
        const nx = px + dir.x * (TILE / 2);
        const ny = py + dir.y * (TILE / 2);
        const c = Math.floor(nx / TILE);
        const r = Math.floor(ny / TILE);
        if (isWall(c, r)) return false;
        if (!allowDoor && isGhostDoor(c, r)) return false;
        return true;
    }

    function wrapTunnel(entity) {
        if (entity.x < -TILE / 2) entity.x = COLS * TILE - TILE / 2;
        else if (entity.x > COLS * TILE - TILE / 2) entity.x = -TILE / 2;
    }

    function atTileCenter(entity) {
        const cx = Math.floor(entity.x / TILE) * TILE + TILE / 2;
        const cy = Math.floor(entity.y / TILE) * TILE + TILE / 2;
        return Math.abs(entity.x + TILE / 2 - cx) < 1 && Math.abs(entity.y + TILE / 2 - cy) < 1;
    }

    function snapToTile(entity) {
        const c = Math.round(entity.x / TILE);
        const r = Math.round(entity.y / TILE);
        entity.x = c * TILE;
        entity.y = r * TILE;
    }

    function updatePacman(dt) {
        if (pacman.dead) return;
        const moveDist = pacman.speed * dt;
        const center = { x: pacman.x + TILE / 2, y: pacman.y + TILE / 2 };
        const offCenterX = Math.abs(center.x - (Math.floor(center.x / TILE) * TILE + TILE / 2));
        const offCenterY = Math.abs(center.y - (Math.floor(center.y / TILE) * TILE + TILE / 2));

        // Try to switch direction at tile centers
        if (pacman.nextDir.x !== pacman.dir.x || pacman.nextDir.y !== pacman.dir.y) {
            if (offCenterX < 2 && offCenterY < 2) {
                if (canMove(pacman.x + TILE / 2, pacman.y + TILE / 2, pacman.nextDir, false)) {
                    snapToTile(pacman);
                    pacman.dir = pacman.nextDir;
                }
            }
        }

        if (canMove(pacman.x + TILE / 2, pacman.y + TILE / 2, pacman.dir, false)) {
            pacman.x += pacman.dir.x * moveDist;
            pacman.y += pacman.dir.y * moveDist;
        } else {
            snapToTile(pacman);
        }

        wrapTunnel(pacman);
        pacman.mouth = (pacman.mouth + dt * 8) % (Math.PI);

        // Eat dot at current tile
        const { c, r } = tileAt(pacman.x + TILE / 2, pacman.y + TILE / 2);
        if (r >= 0 && r < ROWS && c >= 0 && c < COLS) {
            const ch = maze[r][c];
            if (ch === '.') {
                maze[r][c] = ' ';
                score += 10;
                dotsRemaining--;
                updateHud();
            } else if (ch === 'o') {
                maze[r][c] = ' ';
                score += 50;
                dotsRemaining--;
                frightenedTimer = 7;
                ghosts.forEach(g => { if (!g.eaten) g.frightened = true; });
                updateHud();
            }
        }

        if (dotsRemaining <= 0) {
            gameState = 'won';
            messageEl.textContent = 'YOU WIN!';
        }
    }

    function ghostChooseDir(g) {
        const center = { x: g.x + TILE / 2, y: g.y + TILE / 2 };
        const { c, r } = tileAt(center.x, center.y);
        const inHouse = r >= 12 && r <= 15 && c >= 11 && c <= 16;
        const allowDoor = inHouse || g.eaten;

        const options = [
            { x: 0, y: -1 },
            { x: 1, y: 0 },
            { x: 0, y: 1 },
            { x: -1, y: 0 }
        ].filter(d => {
            // No reversing
            if (d.x === -g.dir.x && d.y === -g.dir.y) return false;
            return canMove(center.x, center.y, d, allowDoor);
        });

        if (options.length === 0) {
            g.dir = { x: -g.dir.x, y: -g.dir.y };
            return;
        }

        let target;
        if (g.eaten) {
            target = { c: 13, r: 14 };
        } else if (g.frightened) {
            g.dir = options[Math.floor(Math.random() * options.length)];
            return;
        } else {
            const pc = tileAt(pacman.x + TILE / 2, pacman.y + TILE / 2);
            switch (g.behavior) {
                case 'chase':
                    target = pc;
                    break;
                case 'ambush':
                    target = { c: pc.c + pacman.dir.x * 4, r: pc.r + pacman.dir.y * 4 };
                    break;
                case 'patrol':
                    target = { c: pc.c - pacman.dir.x * 2, r: pc.r - pacman.dir.y * 2 };
                    break;
                case 'random':
                default:
                    const dist = Math.hypot(pc.c - c, pc.r - r);
                    target = dist > 8 ? pc : { c: 1, r: ROWS - 2 };
                    break;
            }
        }

        let best = options[0];
        let bestDist = Infinity;
        for (const d of options) {
            const nc = c + d.x;
            const nr = r + d.y;
            const dist = (nc - target.c) ** 2 + (nr - target.r) ** 2;
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        g.dir = best;
    }

    function updateGhost(g, dt) {
        const speed = g.eaten ? 140 : (g.frightened ? 45 : g.speed);
        const moveDist = speed * dt;
        const center = { x: g.x + TILE / 2, y: g.y + TILE / 2 };
        const offCenterX = Math.abs(center.x - (Math.floor(center.x / TILE) * TILE + TILE / 2));
        const offCenterY = Math.abs(center.y - (Math.floor(center.y / TILE) * TILE + TILE / 2));

        if (offCenterX < 2 && offCenterY < 2) {
            snapToTile(g);
            ghostChooseDir(g);
        }

        const { c, r } = tileAt(g.x + TILE / 2, g.y + TILE / 2);
        const inHouse = r >= 12 && r <= 15 && c >= 11 && c <= 16;
        const allowDoor = inHouse || g.eaten;

        if (canMove(g.x + TILE / 2, g.y + TILE / 2, g.dir, allowDoor)) {
            g.x += g.dir.x * moveDist;
            g.y += g.dir.y * moveDist;
        }

        wrapTunnel(g);

        // Restored from eaten when at home
        if (g.eaten) {
            const cTile = tileAt(g.x + TILE / 2, g.y + TILE / 2);
            if (cTile.c === 13 && cTile.r === 14) {
                g.eaten = false;
                g.frightened = false;
            }
        }
    }

    function checkCollisions() {
        for (const g of ghosts) {
            const dx = (pacman.x + TILE / 2) - (g.x + TILE / 2);
            const dy = (pacman.y + TILE / 2) - (g.y + TILE / 2);
            if (Math.hypot(dx, dy) < TILE * 0.7) {
                if (g.eaten) continue;
                if (g.frightened) {
                    g.eaten = true;
                    g.frightened = false;
                    score += 200;
                    updateHud();
                } else {
                    pacman.dead = true;
                    lives--;
                    updateHud();
                    if (lives <= 0) {
                        gameState = 'over';
                        messageEl.textContent = 'GAME OVER';
                    } else {
                        gameState = 'dying';
                        setTimeout(() => {
                            if (lives > 0) {
                                resetEntities();
                                gameState = 'playing';
                            }
                        }, 1200);
                    }
                    return;
                }
            }
        }
    }

    function drawMaze() {
        for (let r = 0; r < ROWS; r++) {
            for (let c = 0; c < COLS; c++) {
                const ch = maze[r][c];
                const x = c * TILE;
                const y = r * TILE;
                if (ch === '#') {
                    ctx.fillStyle = '#1919a6';
                    ctx.fillRect(x + 1, y + 1, TILE - 2, TILE - 2);
                } else if (ch === '-') {
                    ctx.fillStyle = '#ffb8ff';
                    ctx.fillRect(x, y + TILE / 2 - 1, TILE, 2);
                } else if (ch === '.') {
                    ctx.fillStyle = '#ffb897';
                    ctx.beginPath();
                    ctx.arc(x + TILE / 2, y + TILE / 2, 2, 0, Math.PI * 2);
                    ctx.fill();
                } else if (ch === 'o') {
                    ctx.fillStyle = '#ffb897';
                    ctx.beginPath();
                    ctx.arc(x + TILE / 2, y + TILE / 2, 5, 0, Math.PI * 2);
                    ctx.fill();
                }
            }
        }
    }

    function drawPacman() {
        const cx = pacman.x + TILE / 2;
        const cy = pacman.y + TILE / 2;
        const radius = TILE / 2 - 1;
        const mouthAngle = Math.abs(Math.sin(pacman.mouth)) * 0.6;
        let angle = 0;
        if (pacman.dir.x === 1) angle = 0;
        else if (pacman.dir.x === -1) angle = Math.PI;
        else if (pacman.dir.y === -1) angle = -Math.PI / 2;
        else if (pacman.dir.y === 1) angle = Math.PI / 2;

        ctx.fillStyle = pacman.dead ? '#888' : '#ffcc00';
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.arc(cx, cy, radius, angle + mouthAngle, angle - mouthAngle + Math.PI * 2);
        ctx.closePath();
        ctx.fill();
    }

    function drawGhost(g) {
        const cx = g.x + TILE / 2;
        const cy = g.y + TILE / 2;
        const radius = TILE / 2 - 1;

        if (g.eaten) {
            // Just eyes
            ctx.fillStyle = '#fff';
            ctx.beginPath();
            ctx.arc(cx - 3, cy - 2, 2.5, 0, Math.PI * 2);
            ctx.arc(cx + 3, cy - 2, 2.5, 0, Math.PI * 2);
            ctx.fill();
            return;
        }

        ctx.fillStyle = g.frightened ? '#2121ff' : g.color;
        ctx.beginPath();
        ctx.arc(cx, cy, radius, Math.PI, 0);
        ctx.lineTo(cx + radius, cy + radius);
        // Wavy bottom
        const waves = 3;
        for (let i = 0; i < waves; i++) {
            const wx = cx + radius - ((i * 2 + 1) * radius / waves);
            ctx.lineTo(wx, cy + radius - 2);
            const wx2 = cx + radius - ((i * 2 + 2) * radius / waves);
            ctx.lineTo(wx2, cy + radius);
        }
        ctx.closePath();
        ctx.fill();

        // Eyes
        ctx.fillStyle = '#fff';
        ctx.beginPath();
        ctx.arc(cx - 3, cy - 2, 2.5, 0, Math.PI * 2);
        ctx.arc(cx + 3, cy - 2, 2.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = g.frightened ? '#fff' : '#000';
        ctx.beginPath();
        ctx.arc(cx - 3 + g.dir.x, cy - 2 + g.dir.y, 1.2, 0, Math.PI * 2);
        ctx.arc(cx + 3 + g.dir.x, cy - 2 + g.dir.y, 1.2, 0, Math.PI * 2);
        ctx.fill();
    }

    function draw() {
        ctx.fillStyle = '#000';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        drawMaze();
        ghosts.forEach(drawGhost);
        drawPacman();
    }

    function loop(timestamp) {
        const dt = Math.min((timestamp - lastTime) / 1000, 0.05);
        lastTime = timestamp;

        if (gameState === 'playing') {
            updatePacman(dt);
            ghosts.forEach(g => updateGhost(g, dt));
            checkCollisions();
            if (frightenedTimer > 0) {
                frightenedTimer -= dt;
                if (frightenedTimer <= 0) {
                    ghosts.forEach(g => { g.frightened = false; });
                }
            }
        }

        draw();
        requestAnimationFrame(loop);
    }

    init();
    requestAnimationFrame((t) => { lastTime = t; loop(t); });
})();
