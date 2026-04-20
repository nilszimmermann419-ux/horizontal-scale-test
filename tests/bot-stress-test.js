/**
 * Comprehensive Bot Stress Test for ShardedMC
 * 
 * Usage: cd tests && node bot-stress-test.js [options]
 * Options:
 *   --max-bots N       Maximum number of bots to spawn (default: 50)
 *   --batch-size N     Number of bots per batch (default: 5)
 *   --batch-delay N    Delay between batches in ms (default: 5000)
 *   --duration N       Test duration in minutes (default: 10)
 *   --host H           Server host (default: localhost)
 *   --port P           Server port (default: 25565)
 *   --ramp-up          Gradually increase load over time
 * 
 * Example:
 *   node bot-stress-test.js --max-bots 100 --batch-size 10 --duration 15
 */

const mineflayer = require('mineflayer');
const os = require('os');

// Parse command line arguments
const args = process.argv.slice(2);
const config = {
  maxBots: parseArg('--max-bots', 50),
  batchSize: parseArg('--batch-size', 5),
  batchDelay: parseArg('--batch-delay', 5000),
  duration: parseArg('--duration', 10) * 60 * 1000, // convert to ms
  host: parseStringArg('--host', 'localhost'),
  port: parseArg('--port', 25565),
  rampUp: args.includes('--ramp-up')
};

function parseArg(key, defaultValue) {
  const index = args.indexOf(key);
  if (index !== -1 && args[index + 1]) {
    return parseInt(args[index + 1], 10);
  }
  return defaultValue;
}

function parseStringArg(key, defaultValue) {
  const index = args.indexOf(key);
  if (index !== -1 && args[index + 1]) {
    return args[index + 1];
  }
  return defaultValue;
}

// Statistics tracking
const stats = {
  totalBots: 0,
  activeBots: 0,
  spawnedBots: 0,
  disconnectedBots: 0,
  errors: [],
  kicks: [],
  deaths: 0,
  blocksBroken: 0,
  blocksPlaced: 0,
  messagesSent: 0,
  startTime: Date.now(),
  tpsReadings: [],
  chunkLoadTimes: [],
  memoryReadings: [],
  playerCountReadings: [],
  connectionErrors: 0,
  maxConcurrentPlayers: 0
};

const bots = [];
const botNames = [
  'Alpha', 'Beta', 'Gamma', 'Delta', 'Epsilon', 'Zeta', 'Eta', 'Theta',
  'Iota', 'Kappa', 'Lambda', 'Mu', 'Nu', 'Xi', 'Omicron', 'Pi',
  'Rho', 'Sigma', 'Tau', 'Upsilon', 'Phi', 'Chi', 'Psi', 'Omega',
  'Apollo', 'Athena', 'Zeus', 'Hera', 'Poseidon', 'Hades', 'Hermes', 'Ares',
  'Venus', 'Mars', 'Jupiter', 'Saturn', 'Neptune', 'Pluto', 'Mercury', 'Uranus'
];

function getBotName(index) {
  if (index < botNames.length) {
    return `${botNames[index]}_${index}`;
  }
  return `Bot_${index}_${Date.now()}`;
}

function log(message) {
  const timestamp = new Date().toISOString();
  console.log(`[${timestamp}] ${message}`);
}

// Performance monitoring
function recordPerformance() {
  const memUsage = process.memoryUsage();
  const active = bots.filter(b => b.entity).length;
  
  stats.memoryReadings.push({
    time: Date.now() - stats.startTime,
    heapUsed: Math.round(memUsage.heapUsed / 1024 / 1024),
    heapTotal: Math.round(memUsage.heapTotal / 1024 / 1024),
    rss: Math.round(memUsage.rss / 1024 / 1024)
  });
  
  stats.playerCountReadings.push({
    time: Date.now() - stats.startTime,
    count: active
  });
  
  if (active > stats.maxConcurrentPlayers) {
    stats.maxConcurrentPlayers = active;
  }
}

// Bot behavior functions
function startRandomMovement(bot) {
  const interval = setInterval(() => {
    if (!bot.entity) return;
    
    // Random look direction
    const yaw = Math.random() * Math.PI * 2;
    const pitch = (Math.random() - 0.5) * Math.PI / 2;
    bot.look(yaw, pitch, true);
    
    // Random movement
    const actions = ['forward', 'back', 'left', 'right', 'jump'];
    const action = actions[Math.floor(Math.random() * actions.length)];
    
    bot.setControlState(action, true);
    setTimeout(() => {
      bot.setControlState(action, false);
    }, 500 + Math.random() * 1500);
    
  }, 2000 + Math.random() * 3000);
  
  bot._movementInterval = interval;
}

function startBlockInteraction(bot) {
  const interval = setInterval(async () => {
    if (!bot.entity || !bot.blockAt) return;
    
    try {
      // Break blocks
      if (Math.random() > 0.6) {
        const pos = bot.entity.position;
        const offsets = [
          { x: 1, y: 0, z: 0 }, { x: -1, y: 0, z: 0 },
          { x: 0, y: 0, z: 1 }, { x: 0, y: 0, z: -1 },
          { x: 0, y: -1, z: 0 }
        ];
        const offset = offsets[Math.floor(Math.random() * offsets.length)];
        const block = bot.blockAt(pos.offset(offset.x, offset.y, offset.z));
        
        if (block && block.name !== 'air' && block.diggable) {
          await bot.dig(block);
          stats.blocksBroken++;
        }
      }
      
      // Place blocks
      if (Math.random() > 0.7 && bot.heldItem) {
        const pos = bot.entity.position;
        const offset = {
          x: Math.floor(Math.random() * 3) - 1,
          y: -1,
          z: Math.floor(Math.random() * 3) - 1
        };
        const referenceBlock = bot.blockAt(pos.offset(offset.x, offset.y, offset.z));
        
        if (referenceBlock && referenceBlock.name !== 'air') {
          const faceVector = { x: 0, y: 1, z: 0 };
          await bot.placeBlock(referenceBlock, faceVector);
          stats.blocksPlaced++;
        }
      }
    } catch (err) {
      // Ignore block interaction errors
    }
  }, 5000 + Math.random() * 5000);
  
  bot._blockInterval = interval;
}

function startChatMessages(bot) {
  const messages = [
    'Hello everyone!',
    'This server is awesome!',
    'Anyone here?',
    'Building something cool...',
    'I love Minecraft!',
    'Stress test in progress',
    'Hello world!',
    'Testing 1 2 3',
    'Lag check',
    'Nice server!'
  ];
  
  const interval = setInterval(() => {
    if (!bot.entity) return;
    
    if (Math.random() > 0.5) {
      const message = messages[Math.floor(Math.random() * messages.length)];
      bot.chat(message);
      stats.messagesSent++;
    }
  }, 8000 + Math.random() * 7000);
  
  bot._chatInterval = interval;
}

function monitorChunkLoading(bot) {
  bot.on('chunkColumnLoad', (x, z) => {
    const loadTime = Date.now();
    if (!bot._chunkLoadStart) bot._chunkLoadStart = {};
    bot._chunkLoadStart[`${x},${z}`] = loadTime;
  });
  
  bot.on('chunkColumnUnload', (x, z) => {
    if (bot._chunkLoadStart && bot._chunkLoadStart[`${x},${z}`]) {
      const loadTime = Date.now() - bot._chunkLoadStart[`${x},${z}`];
      stats.chunkLoadTimes.push(loadTime);
      delete bot._chunkLoadStart[`${x},${z}`];
    }
  });
}

function createBot(index) {
  const name = getBotName(index);
  
  const bot = mineflayer.createBot({
    host: config.host,
    port: config.port,
    username: name,
    auth: 'offline',
    checkTimeoutInterval: 60000,
    closeTimeout: 30000
  });
  
  bot._index = index;
  bot._spawnTime = null;
  bot._connected = false;
  
  bot.on('spawn', () => {
    bot._connected = true;
    bot._spawnTime = Date.now();
    stats.spawnedBots++;
    stats.activeBots = bots.filter(b => b.entity).length;
    
    log(`✅ ${name} spawned (${stats.activeBots}/${config.maxBots} active)`);
    
    // Start behaviors
    startRandomMovement(bot);
    startBlockInteraction(bot);
    startChatMessages(bot);
    monitorChunkLoading(bot);
    
    // Log initial position
    if (bot.entity) {
      log(`   Position: ${bot.entity.position.toString()}`);
    }
  });
  
  bot.on('death', () => {
    stats.deaths++;
    log(`💀 ${name} died (total deaths: ${stats.deaths})`);
    bot.respawn();
  });
  
  bot.on('error', (err) => {
    stats.errors.push({
      bot: name,
      error: err.message,
      time: Date.now() - stats.startTime
    });
    
    if (err.message.includes('ECONNREFUSED') || err.message.includes('ETIMEDOUT')) {
      stats.connectionErrors++;
    }
    
    if (stats.errors.length <= 10) {
      log(`❌ ${name} error: ${err.message}`);
    }
  });
  
  bot.on('kicked', (reason) => {
    stats.kicks.push({
      bot: name,
      reason: reason,
      time: Date.now() - stats.startTime
    });
    log(`👢 ${name} kicked: ${reason}`);
  });
  
  bot.on('end', () => {
    bot._connected = false;
    stats.disconnectedBots++;
    stats.activeBots = bots.filter(b => b.entity).length;
    
    // Clear intervals
    if (bot._movementInterval) clearInterval(bot._movementInterval);
    if (bot._blockInterval) clearInterval(bot._blockInterval);
    if (bot._chatInterval) clearInterval(bot._chatInterval);
    
    const lifetime = bot._spawnTime ? Date.now() - bot._spawnTime : 0;
    log(`🔌 ${name} disconnected after ${Math.round(lifetime / 1000)}s`);
    
    // Attempt reconnection if test is still running
    const elapsed = Date.now() - stats.startTime;
    if (elapsed < config.duration && lifetime > 30000) {
      setTimeout(() => {
        if (Date.now() - stats.startTime < config.duration) {
          log(`🔄 Reconnecting ${name}...`);
          createBot(index);
        }
      }, 5000);
    }
  });
  
  bots.push(bot);
  stats.totalBots++;
  return bot;
}

// Spawn bots in batches
function spawnBots() {
  let currentIndex = 0;
  
  const spawnBatch = () => {
    const elapsed = Date.now() - stats.startTime;
    
    if (elapsed >= config.duration) {
      log('⏰ Test duration reached, stopping bot spawning');
      return;
    }
    
    const activeCount = bots.filter(b => b.entity).length;
    
    if (activeCount >= config.maxBots) {
      log(`📊 Maximum bot count reached (${config.maxBots})`);
      return;
    }
    
    const remaining = config.maxBots - activeCount;
    const batchCount = Math.min(config.batchSize, remaining);
    
    log(`🚀 Spawning batch of ${batchCount} bots (${activeCount}/${config.maxBots} active)...`);
    
    for (let i = 0; i < batchCount; i++) {
      setTimeout(() => {
        createBot(currentIndex++);
      }, i * 500);
    }
    
    // Schedule next batch
    if (activeCount + batchCount < config.maxBots) {
      setTimeout(spawnBatch, config.batchDelay);
    }
  };
  
  spawnBatch();
}

// Status reporting
function printStatus() {
  const elapsed = Math.round((Date.now() - stats.startTime) / 1000);
  const active = bots.filter(b => b.entity).length;
  
  log('\n' + '='.repeat(60));
  log('📊 STRESS TEST STATUS REPORT');
  log('='.repeat(60));
  log(`⏱️  Elapsed Time: ${Math.floor(elapsed / 60)}m ${elapsed % 60}s`);
  log(`🤖 Active Bots: ${active}/${bots.length} (max: ${stats.maxConcurrentPlayers})`);
  log(`✅ Spawned: ${stats.spawnedBots} | 🔌 Disconnected: ${stats.disconnectedBots}`);
  log(`💀 Deaths: ${stats.deaths} | ⛏️ Blocks Broken: ${stats.blocksBroken} | 🧱 Placed: ${stats.blocksPlaced}`);
  log(`💬 Messages: ${stats.messagesSent} | ❌ Errors: ${stats.errors.length} | 👢 Kicks: ${stats.kicks.length}`);
  
  // Memory stats
  const latestMem = stats.memoryReadings[stats.memoryReadings.length - 1];
  if (latestMem) {
    log(`💾 Memory: ${latestMem.heapUsed}MB / ${latestMem.heapTotal}MB (RSS: ${latestMem.rss}MB)`);
  }
  
  // TPS estimate (simplified)
  if (stats.chunkLoadTimes.length > 0) {
    const avgChunkLoad = stats.chunkLoadTimes.reduce((a, b) => a + b, 0) / stats.chunkLoadTimes.length;
    log(`🌍 Avg Chunk Load: ${avgChunkLoad.toFixed(2)}ms (${stats.chunkLoadTimes.length} chunks)`);
  }
  
  // Performance trend
  if (stats.playerCountReadings.length >= 2) {
    const recent = stats.playerCountReadings.slice(-5);
    const avgPlayers = recent.reduce((a, b) => a + b.count, 0) / recent.length;
    log(`📈 Avg Players (last 5 readings): ${avgPlayers.toFixed(1)}`);
  }
  
  log('='.repeat(60) + '\n');
}

// Final report
function printFinalReport() {
  const elapsed = Date.now() - stats.startTime;
  
  log('\n' + '='.repeat(70));
  log('🏁 STRESS TEST FINAL REPORT');
  log('='.repeat(70));
  
  log('\n📊 BOT STATISTICS:');
  log(`  Total Bots Created: ${stats.totalBots}`);
  log(`  Successfully Spawned: ${stats.spawnedBots}`);
  log(`  Max Concurrent Players: ${stats.maxConcurrentPlayers}`);
  log(`  Total Disconnections: ${stats.disconnectedBots}`);
  log(`  Connection Errors: ${stats.connectionErrors}`);
  
  log('\n🎮 GAMEPLAY STATISTICS:');
  log(`  Player Deaths: ${stats.deaths}`);
  log(`  Blocks Broken: ${stats.blocksBroken}`);
  log(`  Blocks Placed: ${stats.blocksPlaced}`);
  log(`  Chat Messages: ${stats.messagesSent}`);
  
  log('\n❌ ERROR SUMMARY:');
  log(`  Total Errors: ${stats.errors.length}`);
  log(`  Total Kicks: ${stats.kicks.length}`);
  
  if (stats.errors.length > 0) {
    log('\n  Recent Errors:');
    const recent = stats.errors.slice(-5);
    recent.forEach(e => {
      log(`    - ${e.bot}: ${e.error}`);
    });
  }
  
  if (stats.kicks.length > 0) {
    log('\n  Kick Reasons:');
    const kickCounts = {};
    stats.kicks.forEach(k => {
      kickCounts[k.reason] = (kickCounts[k.reason] || 0) + 1;
    });
    Object.entries(kickCounts).forEach(([reason, count]) => {
      log(`    - "${reason}": ${count} times`);
    });
  }
  
  log('\n💾 MEMORY STATISTICS:');
  if (stats.memoryReadings.length > 0) {
    const mems = stats.memoryReadings.map(m => m.heapUsed);
    const avgMem = mems.reduce((a, b) => a + b, 0) / mems.length;
    const maxMem = Math.max(...mems);
    log(`  Average Heap: ${avgMem.toFixed(1)}MB`);
    log(`  Max Heap: ${maxMem}MB`);
    log(`  Final Heap: ${mems[mems.length - 1]}MB`);
  }
  
  log('\n🌍 PERFORMANCE STATISTICS:');
  if (stats.chunkLoadTimes.length > 0) {
    const times = stats.chunkLoadTimes;
    const avg = times.reduce((a, b) => a + b, 0) / times.length;
    const max = Math.max(...times);
    const min = Math.min(...times);
    log(`  Chunk Load Times: avg=${avg.toFixed(2)}ms min=${min}ms max=${max}ms`);
    log(`  Total Chunks Loaded: ${times.length}`);
  }
  
  log('\n📈 PLAYER COUNT OVER TIME:');
  if (stats.playerCountReadings.length > 0) {
    const readings = stats.playerCountReadings;
    log(`  Start: ${readings[0].count} players`);
    log(`  Peak: ${stats.maxConcurrentPlayers} players`);
    log(`  End: ${readings[readings.length - 1].count} players`);
    log(`  Data Points: ${readings.length}`);
  }
  
  // Calculate stability score
  const successfulSpawns = stats.spawnedBots / Math.max(stats.totalBots, 1);
  const connectionStability = 1 - (stats.connectionErrors / Math.max(stats.totalBots, 1));
  const kickRate = stats.kicks.length / Math.max(stats.totalBots, 1);
  
  log('\n🏆 STABILITY SCORE:');
  log(`  Spawn Success Rate: ${(successfulSpawns * 100).toFixed(1)}%`);
  log(`  Connection Stability: ${(connectionStability * 100).toFixed(1)}%`);
  log(`  Kick Rate: ${(kickRate * 100).toFixed(1)}%`);
  
  const overallScore = ((successfulSpawns * 0.4) + (connectionStability * 0.4) + ((1 - kickRate) * 0.2)) * 100;
  log(`  Overall Score: ${overallScore.toFixed(1)}/100`);
  
  if (overallScore >= 90) {
    log('  Status: EXCELLENT ✅');
  } else if (overallScore >= 70) {
    log('  Status: GOOD 👍');
  } else if (overallScore >= 50) {
    log('  Status: FAIR ⚠️');
  } else {
    log('  Status: POOR ❌');
  }
  
  log('\n' + '='.repeat(70));
}

// Main execution
log('🤖 ShardedMC Bot Stress Test');
log('='.repeat(50));
log(`Configuration:`);
log(`  Max Bots: ${config.maxBots}`);
log(`  Batch Size: ${config.batchSize}`);
log(`  Batch Delay: ${config.batchDelay}ms`);
log(`  Duration: ${config.duration / 60000} minutes`);
log(`  Target: ${config.host}:${config.port}`);
log(`  Ramp Up: ${config.rampUp ? 'Yes' : 'No'}`);
log('='.repeat(50) + '\n');

// Start performance monitoring
const perfInterval = setInterval(recordPerformance, 5000);

// Start status reporting
const statusInterval = setInterval(printStatus, 30000);

// Spawn bots
spawnBots();

// Duration timeout
setTimeout(() => {
  log('⏰ Test duration reached, shutting down...');
  
  clearInterval(perfInterval);
  clearInterval(statusInterval);
  
  // Print final report
  printFinalReport();
  
  // Disconnect all bots
  log('\n🔌 Disconnecting all bots...');
  bots.forEach(bot => {
    try {
      bot.end();
    } catch (e) {
      // Ignore
    }
  });
  
  // Exit after cleanup
  setTimeout(() => {
    process.exit(0);
  }, 2000);
}, config.duration);

// Handle Ctrl+C
process.on('SIGINT', () => {
  log('\n🛑 Test interrupted by user');
  
  clearInterval(perfInterval);
  clearInterval(statusInterval);
  
  printFinalReport();
  
  log('\n🔌 Disconnecting all bots...');
  bots.forEach(bot => {
    try {
      bot.end();
    } catch (e) {
      // Ignore
    }
  });
  
  setTimeout(() => {
    process.exit(0);
  }, 2000);
});

// Handle uncaught errors
process.on('uncaughtException', (err) => {
  log(`⚠️ Uncaught exception: ${err.message}`);
});

process.on('unhandledRejection', (reason) => {
  log(`⚠️ Unhandled rejection: ${reason}`);
});