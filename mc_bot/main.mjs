import { Worker } from 'worker_threads'
import logUpdate from 'log-update';

let bot_id = 1;
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function insertRow(servers_data_array, row) {
    if(!servers_data_array[row.id]) {
        servers_data_array[row.id] = {
            tps: [],
            mspt: [],
            latency: [],
            min: {
                tps: Number.MAX_VALUE,
                mspt: Number.MAX_VALUE,
                latency: Number.MAX_VALUE,
            },
            max: {
                tps: Number.MIN_VALUE,
                mspt: Number.MIN_VALUE,
                latency: Number.MIN_VALUE,
            },
            avg: {
                tps: 0,
                mspt: 0,
                latency: 0,
            },
            count: 0
        };
    }
    servers_data_array[row.id].tps.push(row.tps);
    servers_data_array[row.id].mspt.push(row.mspt);
    servers_data_array[row.id].latency.push(row.latency);

    servers_data_array[row.id].min.tps = Math.min(servers_data_array[row.id].min.tps, row.tps);
    servers_data_array[row.id].min.mspt = Math.min(servers_data_array[row.id].min.mspt, row.mspt);
    servers_data_array[row.id].min.latency = Math.min(servers_data_array[row.id].min.latency, row.latency);

    servers_data_array[row.id].max.tps = Math.max(servers_data_array[row.id].max.tps, row.tps);
    servers_data_array[row.id].max.mspt = Math.max(servers_data_array[row.id].max.mspt, row.mspt);
    servers_data_array[row.id].max.latency = Math.max(servers_data_array[row.id].max.latency, row.latency);

    servers_data_array[row.id].avg.tps = (servers_data_array[row.id].avg.tps * servers_data_array[row.id].count + row.tps) / (servers_data_array[row.id].count + 1);
    servers_data_array[row.id].avg.mspt = (servers_data_array[row.id].avg.mspt * servers_data_array[row.id].count + row.mspt) / (servers_data_array[row.id].count + 1);
    servers_data_array[row.id].avg.latency = (servers_data_array[row.id].avg.latency * servers_data_array[row.id].count + row.latency) / (servers_data_array[row.id].count + 1);

    servers_data_array[row.id].count++;
}

function logResults(servers_data_array, info) {
    let logData = `\nPerformance Benchmark Results (Batch: ${info.batch_id})\n\n`;
    logData += Object.entries(servers_data_array).map(([id, data]) => {
        return `ID: ${id} count: ${data.count}
- TPS: min: ${data.min.tps.toFixed(2)}, max: ${data.max.tps.toFixed(2)}, avg: ${data.avg.tps.toFixed(2)}
- MSPT: min: ${data.min.mspt.toFixed(2)}, max: ${data.max.mspt.toFixed(2)}, avg: ${data.avg.mspt.toFixed(2)}
- Ping: min: ${data.min.latency.toFixed(2)}ms, max: ${data.max.latency.toFixed(2)}ms, avg: ${data.avg.latency.toFixed(2)}ms`;
    }).join('\n\n');

    logData += `\n\nBatch Time: ${info.batch_time / 1000}s / ${batch_duration / 1000}s\n\n`;

    logUpdate(logData);
}

let bot_length = 0, expected_length = 0;

function createBot(worker, ip, port) {
    return new Promise((resolve) => {
        expected_length++;
        worker.expected_length++;
        worker.postMessage({ type: 'createBot', data: { ip, port, botName: `BenchmarkBot${bot_id}` } });
        bot_id++;
        const onMessage = (message) => {
            if (message.type === 'botCreated') {
                bot_length++;
                worker.bot_length++;
                resolve(true);
            } else if (message.type === 'error') {
                expected_length--;
                worker.expected_length--;
                resolve(false);
            } else if (message.type === 'bot_disconnected') {
                expected_length--;
                worker.expected_length--;
                resolve(false);
            }
            else {
                worker.once('message', onMessage);
            }
        }
        worker.once('message', onMessage);
    });
}

let workers = [];

async function runBenchmark(botCount, batch_count, ip, port) {
    let servers_data_array = {};
    
    for (let i = 0; i < batch_count; i++) {
        let batch_time = 0;
        while (bot_length < botCount * (i + 1)) {
            let log_message = `Creating bot ${bot_id}...`;
            if(workers.length == 0 || workers[workers.length - 1].expected_length >= worker_bot_count) {
                let worker = new Worker('./worker.mjs');
                workers.push(worker);
                worker.bot_length = 0;
                worker.expected_length = 0;
            }
            logUpdate(log_message);
            
            let worker = workers[workers.length - 1];
            await createBot(worker, ip, port);

            await sleep(1000);
        }
        logUpdate.done();
        while (batch_time < batch_duration) {

            let results = [];
        
            // Collect metrics

            let tasks = []


            for (const worker of workers) {
                worker.postMessage({ type: 'getMetrics' });
                tasks.push(new Promise((resolve, reject) => {
                    const onMessage = (message) => {
                        if (message.type === 'metrics') {
                            resolve(message.data);
                        }
                        else if (message.type === 'error') {
                            reject(new Error(message.data));
                        }
                        else if (message.type === 'bot_disconnected') {
                            reject(new Error(`Bot ${message.data} disconnected`));
                        }
                        else {
                            worker.once('message', onMessage);
                        }
                    }
                    worker.once('message', onMessage);
                }));
            }

            // Wait for all workers to finish collecting metrics
            let task_results = await Promise.all(tasks);

            for (const result of task_results) {
                for (const row of result) {
                    results.push(row);
                }
            }

            for(const row of results) {
                insertRow(servers_data_array, row);
            }
            batch_time += batch_interval;
            logResults(servers_data_array, {batch_id: i + 1, batch_time: batch_time});
            await sleep(batch_interval);
        }
        logUpdate.done();
    }

    // Disconnect all bots after the benchmark is complete
    for (const worker of workers) {
        worker.postMessage({ type: 'disconnect' });
        worker.on('message', (message) => {
            if (message.type === 'all_disconnected') {
                worker.terminate();
            }
        });
    }
}

// get command line arguments
const args = process.argv.slice(2);
if (args.length < 1 || args.length > 5) {
    console.error('Usage: node main.mjs <ip> <port> <botCount> <batch_count> <batch_duration>');
    process.exit(1);
}
const ip = args[0] || 'localhost';
const port = parseInt(args[1]) || 25565;
const botCount = parseInt(args[2]) || 20;
const batch_count = parseInt(args[3]) || 1;
const batch_duration = (parseInt(args[4]) || 60) * 1000;

const batch_interval = 1000 * 1;
const worker_bot_count = 1

runBenchmark(botCount, batch_count, ip, port).catch(async(error) => {
    // Handle ctrl+c
    if (error.code === 'ERR_UNHANDLED_REJECTION') {
        console.error('Benchmark interrupted by user.');
    } else {
        console.error(`Error: ${error.message}`);
    } 
    // Disconnect all bots
    for (const worker of workers) {
        worker.postMessage({ type: 'disconnect' });
        worker.on('message', (message) => {
            if (message.type === 'all_disconnected') {
                worker.terminate();
            }
        });
    }
    setTimeout(() => {
        process.exit(0);
    }, 5000);
})