/**
 * Run multiple bots to test the sharded server
 * 
 * Usage: cd tests && node run-bots.js [port]
 * If port is specified, all bots will connect to that port.
 * Otherwise, bots will be distributed between ports 25565 and 25566.
 */

const mineflayer = require('mineflayer');

const DEFAULT_HOST = process.env.SHARDEDMC_HOST || 'localhost';
const DEFAULT_PORT = parseInt(process.env.SHARDEDMC_PORT, 10) || 25565;
const DEFAULT_PORT_BETA = parseInt(process.env.SHARDEDMC_PORT_BETA, 10) || 25566;

const SHARD_ALPHA = `${DEFAULT_HOST}:${DEFAULT_PORT}`;
const SHARD_BETA = `${DEFAULT_HOST}:${DEFAULT_PORT_BETA}`;

// Parse command line arguments
const args = process.argv.slice(2);
const fixedPort = args[0] ? parseInt(args[0], 10) : null;

console.log('🤖 Starting ShardedMC Bot Test\n');
if (fixedPort) {
    console.log(`Connecting all bots to port ${fixedPort}\n`);
}

// Track active bots and statistics
const bots = [];
const stats = {
    deaths: 0,
    errors: [],
    kicks: [],
    blocksBroken: 0
};

const botNames = [
    { name: 'Steve', port: 25565 },
    { name: 'Alex', port: 25566 },
    { name: 'Notch', port: 25565 },
    { name: 'Herobrine', port: 25566 },
    { name: 'Villager', port: 25565 }
];

function createBot(name, port) {
    const bot = mineflayer.createBot({
        host: DEFAULT_HOST,
        port: fixedPort || port,
        username: name,
        auth: 'offline',
        checkTimeoutInterval: 60000
    });
    
    bot.on('spawn', () => {
        console.log(`✅ ${name} connected to port ${fixedPort || port}`);
        
        // Random movement every 3-7 seconds
        const moveInterval = setInterval(() => {
            if (bot.entity) {
                const yaw = Math.random() * Math.PI * 2;
                const pitch = (Math.random() - 0.5) * Math.PI / 3;
                bot.look(yaw, pitch, true);
                
                // Random movement
                const actions = ['forward', 'back', 'left', 'right'];
                const action = actions[Math.floor(Math.random() * actions.length)];
                
                if (Math.random() > 0.3) {
                    bot.setControlState(action, true);
                    setTimeout(() => {
                        bot.setControlState(action, false);
                    }, 500 + Math.random() * 1500);
                }
                
                // Occasionally jump
                if (Math.random() > 0.7) {
                    bot.setControlState('jump', true);
                    setTimeout(() => bot.setControlState('jump', false), 250);
                }
            }
        }, 3000 + Math.random() * 4000);
        
        // Occasionally break blocks
        const blockInterval = setInterval(() => {
            if (bot.entity && Math.random() > 0.7) {
                const block = bot.blockAt(bot.entity.position.offset(0, -1, 0));
                if (block && block.name !== 'air' && block.diggable) {
                    bot.dig(block).catch(() => {});
                    stats.blocksBroken++;
                }
            }
        }, 8000);
        
        // Store intervals for cleanup
        bot._intervals = [moveInterval, blockInterval];
    });
    
    bot.on('death', () => {
        stats.deaths++;
        console.log(`💀 ${name} died (total: ${stats.deaths})`);
        bot.respawn();
    });
    
    bot.on('error', (err) => {
        stats.errors.push({ bot: name, error: err.message });
        if (stats.errors.length <= 5) {
            console.error(`❌ ${name} error:`, err.message);
        }
    });
    
    bot.on('kicked', (reason) => {
        stats.kicks.push({ bot: name, reason });
        console.log(`👢 ${name} kicked:`, reason);
    });
    
    bot.on('end', () => {
        console.log(`🔌 ${name} disconnected`);
        // Clear intervals
        if (bot._intervals) {
            bot._intervals.forEach(i => clearInterval(i));
        }
    });
    
    bots.push(bot);
    return bot;
}

// Create all bots with delay
console.log('Creating bots...\n');
botNames.forEach((config, index) => {
    setTimeout(() => {
        console.log(`Creating ${config.name} on port ${fixedPort || config.port}...`);
        createBot(config.name, config.port);
    }, index * 2000);
});

// Status report every 30 seconds
setInterval(() => {
    const active = bots.filter(b => b.entity).length;
    console.log('\n📊 Status Report:');
    console.log(`Active bots: ${active}/${bots.length}`);
    console.log(`Deaths: ${stats.deaths} | Blocks broken: ${stats.blocksBroken}`);
    console.log(`Errors: ${stats.errors.length} | Kicks: ${stats.kicks.length}`);
    
    if (active > 0) {
        console.log('Positions:');
        bots.forEach(bot => {
            if (bot.entity) {
                console.log(`  ${bot.username}: ${bot.entity.position.toString()}`);
            }
        });
    }
}, 30000);

// Keep running
console.log('Bots will run for 5 minutes...');
setTimeout(() => {
    console.log('\n📊 Final Statistics:');
    console.log(`Deaths: ${stats.deaths}`);
    console.log(`Blocks broken: ${stats.blocksBroken}`);
    console.log(`Errors: ${stats.errors.length}`);
    console.log(`Kicks: ${stats.kicks.length}`);
    
    console.log('\nStopping all bots...');
    bots.forEach(bot => {
        try { bot.end(); } catch(e) {}
    });
    process.exit(0);
}, 300000);

// Handle Ctrl+C
process.on('SIGINT', () => {
    console.log('\nStopping all bots...');
    bots.forEach(bot => {
        try { bot.end(); } catch(e) {}
    });
    process.exit(0);
});
