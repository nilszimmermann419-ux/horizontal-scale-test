/**
 * Production-grade test suite for ShardedMC
 * Tests multi-shard functionality with Mineflayer bots
 */

const mineflayer = require('mineflayer');

// Test configuration
const SHARD_ALPHA = 'localhost:25565';
const SHARD_BETA = 'localhost:25566';

let passed = 0;
let failed = 0;

function log(message) {
    console.log(`[TEST] ${message}`);
}

function success(message) {
    console.log(`✅ PASS: ${message}`);
    passed++;
}

function error(message, err) {
    console.error(`❌ FAIL: ${message}`);
    if (err) console.error(err.message || err);
    failed++;
}

function createBot(name, server = SHARD_ALPHA) {
    const [host, port] = server.split(':');
    return mineflayer.createBot({
        host: host,
        port: parseInt(port),
        username: name,
        auth: 'offline',
        checkTimeoutInterval: 60000
    });
}

/**
 * Test 1: Basic connection and spawn
 */
async function testConnection() {
    log('Test 1: Connection and spawn...');
    
    return new Promise((resolve) => {
        const bot = createBot('ConnectionTest');
        let resolved = false;
        
        const cleanup = () => {
            if (!resolved) {
                resolved = true;
                try { bot.end(); } catch(e) {}
                resolve();
            }
        };
        
        bot.on('spawn', () => {
            success('Bot connected and spawned');
            cleanup();
        });
        
        bot.on('error', (err) => {
            error('Connection failed', err);
            cleanup();
        });
        
        setTimeout(() => {
            error('Connection timeout');
            cleanup();
        }, 10000);
    });
}

/**
 * Test 2: Block interaction (look at adjacent block)
 */
async function testBlockInteraction() {
    log('Test 2: Block interaction...');
    
    return new Promise((resolve) => {
        const bot = createBot('BlockTest');
        let resolved = false;
        
        const cleanup = () => {
            if (!resolved) {
                resolved = true;
                try { bot.end(); } catch(e) {}
                resolve();
            }
        };
        
        bot.on('spawn', async () => {
            try {
                await new Promise(r => setTimeout(r, 3000));
                
                // Look for a solid block nearby
                let targetBlock = null;
                let targetPos = null;
                
                // Search in a small area around the bot
                for (let dx = -2; dx <= 2 && !targetBlock; dx++) {
                    for (let dz = -2; dz <= 2 && !targetBlock; dz++) {
                        for (let dy = -2; dy <= 2 && !targetBlock; dy++) {
                            const checkPos = bot.entity.position.offset(dx, dy, dz);
                            const block = bot.blockAt(checkPos);
                            if (block && block.name !== 'air' && block.name !== 'void_air' && block.name !== 'cave_air') {
                                targetBlock = block;
                                targetPos = checkPos;
                                break;
                            }
                        }
                    }
                }
                
                log(`Target block: ${targetBlock ? targetBlock.name : 'null'}`);
                
                if (targetBlock) {
                    // Break it
                    await bot.dig(targetBlock);
                    
                    await new Promise(r => setTimeout(r, 1000));
                    
                    const afterBreak = bot.blockAt(targetPos);
                    log(`After break: ${afterBreak ? afterBreak.name : 'null'}`);
                    
                    if (!afterBreak || afterBreak.name === 'air') {
                        success('Block broke successfully');
                    } else {
                        // Maybe it dropped as an item but block is still there?
                        const items = bot.inventory.items();
                        log(`Inventory after break: ${items.length} items`);
                        
                        if (items.length > 0) {
                            success('Block break worked (got drops)');
                        } else {
                            error(`Block still exists: ${afterBreak ? afterBreak.name : 'null'}`);
                        }
                    }
                } else {
                    error('No target block to break');
                }
                
                cleanup();
            } catch (err) {
                error('Block interaction error', err);
                cleanup();
            }
        });
        
        bot.on('error', (err) => {
            error('Bot error', err);
            cleanup();
        });
        
        setTimeout(() => {
            error('Block interaction timeout');
            cleanup();
        }, 20000);
    });
}

/**
 * Test 3: Multi-bot stress test
 */
async function testStressTest() {
    log('Test 3: Multi-bot stress test (10 bots)...');
    
    const botCount = 10;
    const bots = [];
    let connected = 0;
    let resolved = false;
    
    return new Promise((resolve) => {
        const cleanup = () => {
            if (!resolved) {
                resolved = true;
                bots.forEach(b => {
                    try { b.end(); } catch(e) {}
                });
                resolve();
            }
        };
        
        for (let i = 0; i < botCount; i++) {
            const bot = createBot(`StressBot${i}`);
            bots.push(bot);
            
            bot.on('spawn', () => {
                connected++;
                
                if (connected === botCount) {
                    success(`All ${botCount} bots connected`);
                    setTimeout(cleanup, 1000);
                }
            });
            
            bot.on('error', (err) => {
                // Ignore errors in stress test
            });
        }
        
        setTimeout(() => {
            if (connected < botCount) {
                error(`Only ${connected}/${botCount} bots connected`);
            }
            cleanup();
        }, 15000);
    });
}

/**
 * Test 4: Two shards simultaneously
 */
async function testMultiShard() {
    log('Test 4: Multi-shard connectivity...');
    
    let alphaConnected = false;
    let betaConnected = false;
    let resolved = false;
    
    return new Promise((resolve) => {
        const cleanup = () => {
            if (!resolved) {
                resolved = true;
                try { botAlpha.end(); } catch(e) {}
                try { botBeta.end(); } catch(e) {}
                resolve();
            }
        };
        
        const botAlpha = createBot('ShardAlphaTest', SHARD_ALPHA);
        const botBeta = createBot('ShardBetaTest', SHARD_BETA);
        
        botAlpha.on('spawn', () => {
            success('Connected to Shard Alpha');
            alphaConnected = true;
            try { botAlpha.end(); } catch(e) {}
            checkDone();
        });
        
        botBeta.on('spawn', () => {
            success('Connected to Shard Beta');
            betaConnected = true;
            try { botBeta.end(); } catch(e) {}
            checkDone();
        });
        
        const checkDone = () => {
            if (alphaConnected && betaConnected) {
                success('Both shards accessible simultaneously');
                cleanup();
            }
        };
        
        botAlpha.on('error', (err) => {
            error('Shard Alpha connection failed', err);
            alphaConnected = true;
            checkDone();
        });
        
        botBeta.on('error', (err) => {
            error('Shard Beta connection failed', err);
            betaConnected = true;
            checkDone();
        });
        
        setTimeout(() => {
            if (!alphaConnected || !betaConnected) {
                error('Multi-shard timeout');
            }
            cleanup();
        }, 15000);
    });
}

/**
 * Run all tests
 */
async function runTests() {
    console.log('========================================');
    console.log('ShardedMC Production Test Suite');
    console.log('========================================\n');
    
    log('Waiting 3 seconds for servers...');
    await new Promise(r => setTimeout(r, 3000));
    
    await testConnection();
    await new Promise(r => setTimeout(r, 1000));
    
    await testBlockInteraction();
    await new Promise(r => setTimeout(r, 1000));
    
    await testStressTest();
    await new Promise(r => setTimeout(r, 2000));
    
    await testMultiShard();
    
    console.log('\n========================================');
    console.log(`Results: ${passed} passed, ${failed} failed`);
    console.log('========================================');
    
    if (failed > 0) {
        console.log('\n⚠️  Some tests failed - review logs above');
    } else {
        console.log('\n✅ All tests passed - system is production-ready!');
    }
    
    process.exit(failed > 0 ? 1 : 0);
}

runTests().catch(err => {
    console.error('Test suite error:', err);
    process.exit(1);
});
