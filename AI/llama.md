神经网络大模型是端到端的模型，输入和输出端起到了决定性的作用，在LoRA微调的过程中训练的两个低秩矩阵，本质就是对这两个端的训练

安装nvitop、vllm：
```shell
pip install nvitop
pip install -e ".[vllm]"
```

安装llamafactory：
```shell
git clone https://github.com/hiyouga/LLaMA-Factory.git
cd LLaMA-Factory
pip install -e .
```

启动webui：
```shell
cd LLaMA-Factory
llamafactory-cli webui
```

训练参数：
```shell
llamafactory-cli train \
    --stage sft \
    --do_train True \
    --model_name_or_path /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct \
    --preprocessing_num_workers 16 \
    --finetuning_type lora \
    --template llama3 \
    --flash_attn auto \
    --dataset_dir data \
    --dataset identity,fintech \
    --cutoff_len 1024 \
    --learning_rate 5e-05 \
    --num_train_epochs 10.0 \
    --max_samples 1000 \
    --per_device_train_batch_size 2 \
    --gradient_accumulation_steps 8 \
    --lr_scheduler_type cosine \
    --max_grad_norm 1.0 \
    --logging_steps 5 \
    --save_steps 100 \
    --warmup_steps 0 \
    --packing False \
    --report_to none \
    --output_dir saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03 \
    --bf16 True \
    --plot_loss True \
    --trust_remote_code True \
    --ddp_timeout 180000000 \
    --include_num_input_tokens_seen True \
    --optim adamw_torch \
    --lora_rank 8 \
    --lora_alpha 16 \
    --lora_dropout 0 \
    --lora_target all 
```

日志：
```shell
[INFO|2025-04-30 22:55:20] llamafactory.hparams.parser:401 >> Process rank: 0, world size: 1, device: cuda:0, distributed training: False, compute dtype: torch.bfloat16
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,341 >> loading file tokenizer.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,341 >> loading file tokenizer.model
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,341 >> loading file added_tokens.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,341 >> loading file special_tokens_map.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,342 >> loading file tokenizer_config.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,342 >> loading file chat_template.jinja
[INFO|tokenization_utils_base.py:2323] 2025-04-30 22:55:20,715 >> Special tokens have been added in the vocabulary, make sure the associated word embeddings are fine-tuned or trained.
[INFO|configuration_utils.py:691] 2025-04-30 22:55:20,716 >> loading configuration file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/config.json
[INFO|configuration_utils.py:765] 2025-04-30 22:55:20,718 >> Model config LlamaConfig {
  "architectures": [
    "LlamaForCausalLM"
  ],
  "attention_bias": false,
  "attention_dropout": 0.0,
  "bos_token_id": 128000,
  "eos_token_id": 128009,
  "head_dim": 128,
  "hidden_act": "silu",
  "hidden_size": 4096,
  "initializer_range": 0.02,
  "intermediate_size": 14336,
  "max_position_embeddings": 8192,
  "mlp_bias": false,
  "model_type": "llama",
  "num_attention_heads": 32,
  "num_hidden_layers": 32,
  "num_key_value_heads": 8,
  "pretraining_tp": 1,
  "rms_norm_eps": 1e-05,
  "rope_scaling": null,
  "rope_theta": 500000.0,
  "tie_word_embeddings": false,
  "torch_dtype": "bfloat16",
  "transformers_version": "4.51.3",
  "use_cache": true,
  "vocab_size": 128256
}

[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,719 >> loading file tokenizer.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,719 >> loading file tokenizer.model
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,719 >> loading file added_tokens.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,719 >> loading file special_tokens_map.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,719 >> loading file tokenizer_config.json
[INFO|tokenization_utils_base.py:2058] 2025-04-30 22:55:20,719 >> loading file chat_template.jinja
[INFO|tokenization_utils_base.py:2323] 2025-04-30 22:55:21,077 >> Special tokens have been added in the vocabulary, make sure the associated word embeddings are fine-tuned or trained.
[INFO|2025-04-30 22:55:21] llamafactory.data.template:143 >> Add pad token: <|eot_id|>
[INFO|2025-04-30 22:55:21] llamafactory.data.template:143 >> Add <|eom_id|> to stop words.
[WARNING|2025-04-30 22:55:21] llamafactory.data.template:148 >> New tokens have been added, make sure `resize_vocab` is True.
[INFO|2025-04-30 22:55:21] llamafactory.data.loader:143 >> Loading dataset identity.json...
Setting num_proc from 16 back to 1 for the train split to disable multiprocessing as it only contains one shard.
Generating train split: 91 examples [00:00, 14174.68 examples/s]
Converting format of dataset (num_proc=16): 100%|██████████████████████████████████████████████| 91/91 [00:00<00:00, 571.29 examples/s]
[INFO|2025-04-30 22:55:22] llamafactory.data.loader:143 >> Loading dataset fintech.json...
Setting num_proc from 16 back to 1 for the train split to disable multiprocessing as it only contains one shard.
Generating train split: 400 examples [00:00, 16845.27 examples/s]
Converting format of dataset (num_proc=16): 100%|███████████████████████████████████████████| 400/400 [00:00<00:00, 2508.25 examples/s]
Running tokenizer on dataset (num_proc=16): 100%|████████████████████████████████████████████| 491/491 [00:03<00:00, 147.00 examples/s]
training example:
input_ids:
[128000, 128006, 882, 128007, 271, 6151, 128009, 128006, 78191, 128007, 271, 9906, 0, 358, 1097, 103036, 111200, 11, 459, 15592, 18328, 8040, 555, 1676, 263, 13, 2650, 649, 358, 7945, 499, 3432, 30, 128009]
inputs:
<|begin_of_text|><|start_header_id|>user<|end_header_id|>

hi<|eot_id|><|start_header_id|>assistant<|end_header_id|>

Hello! I am 小聚, an AI assistant developed by Aron. How can I assist you today?<|eot_id|>
label_ids:
[-100, -100, -100, -100, -100, -100, -100, -100, -100, -100, -100, 9906, 0, 358, 1097, 103036, 111200, 11, 459, 15592, 18328, 8040, 555, 1676, 263, 13, 2650, 649, 358, 7945, 499, 3432, 30, 128009]
labels:
Hello! I am 小聚, an AI assistant developed by Aron. How can I assist you today?<|eot_id|>
[INFO|configuration_utils.py:691] 2025-04-30 22:55:27,040 >> loading configuration file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/config.json
[INFO|configuration_utils.py:765] 2025-04-30 22:55:27,041 >> Model config LlamaConfig {
  "architectures": [
    "LlamaForCausalLM"
  ],
  "attention_bias": false,
  "attention_dropout": 0.0,
  "bos_token_id": 128000,
  "eos_token_id": 128009,
  "head_dim": 128,
  "hidden_act": "silu",
  "hidden_size": 4096,
  "initializer_range": 0.02,
  "intermediate_size": 14336,
  "max_position_embeddings": 8192,
  "mlp_bias": false,
  "model_type": "llama",
  "num_attention_heads": 32,
  "num_hidden_layers": 32,
  "num_key_value_heads": 8,
  "pretraining_tp": 1,
  "rms_norm_eps": 1e-05,
  "rope_scaling": null,
  "rope_theta": 500000.0,
  "tie_word_embeddings": false,
  "torch_dtype": "bfloat16",
  "transformers_version": "4.51.3",
  "use_cache": true,
  "vocab_size": 128256
}

[INFO|2025-04-30 22:55:27] llamafactory.model.model_utils.kv_cache:143 >> KV cache is disabled during training.
[INFO|modeling_utils.py:1121] 2025-04-30 22:55:27,093 >> loading weights file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/model.safetensors.index.json
[INFO|modeling_utils.py:2167] 2025-04-30 22:55:27,093 >> Instantiating LlamaForCausalLM model under default dtype torch.bfloat16.
[INFO|configuration_utils.py:1142] 2025-04-30 22:55:27,095 >> Generate config GenerationConfig {
  "bos_token_id": 128000,
  "eos_token_id": 128009,
  "use_cache": false
}

Loading checkpoint shards: 100%|█████████████████████████████████████████████████████████████████████████| 4/4 [00:02<00:00,  1.47it/s]
[INFO|modeling_utils.py:4930] 2025-04-30 22:55:29,870 >> All model checkpoint weights were used when initializing LlamaForCausalLM.

[INFO|modeling_utils.py:4938] 2025-04-30 22:55:29,870 >> All the weights of LlamaForCausalLM were initialized from the model checkpoint at /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct.
If your task is similar to the task the model of the checkpoint was trained on, you can already use LlamaForCausalLM for predictions without further training.
[INFO|configuration_utils.py:1095] 2025-04-30 22:55:29,873 >> loading configuration file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/generation_config.json
[INFO|configuration_utils.py:1142] 2025-04-30 22:55:29,873 >> Generate config GenerationConfig {
  "bos_token_id": 128000,
  "do_sample": true,
  "eos_token_id": [
    128001,
    128009
  ],
  "max_length": 4096,
  "temperature": 0.6,
  "top_p": 0.9
}

[INFO|2025-04-30 22:55:29] llamafactory.model.model_utils.checkpointing:143 >> Gradient checkpointing enabled.
[INFO|2025-04-30 22:55:29] llamafactory.model.model_utils.attention:143 >> Using torch SDPA for faster training and inference.
[INFO|2025-04-30 22:55:29] llamafactory.model.adapter:143 >> Upcasting trainable params to float32.
[INFO|2025-04-30 22:55:29] llamafactory.model.adapter:143 >> Fine-tuning method: LoRA
[INFO|2025-04-30 22:55:29] llamafactory.model.model_utils.misc:143 >> Found linear modules: q_proj,v_proj,k_proj,down_proj,up_proj,o_proj,gate_proj
[INFO|2025-04-30 22:55:30] llamafactory.model.loader:143 >> trainable params: 20,971,520 || all params: 8,051,232,768 || trainable%: 0.2605
[INFO|trainer.py:748] 2025-04-30 22:55:30,411 >> Using auto half precision backend
[INFO|trainer.py:2414] 2025-04-30 22:55:30,692 >> ***** Running training *****
[INFO|trainer.py:2415] 2025-04-30 22:55:30,692 >>   Num examples = 481
[INFO|trainer.py:2416] 2025-04-30 22:55:30,692 >>   Num Epochs = 10
[INFO|trainer.py:2417] 2025-04-30 22:55:30,692 >>   Instantaneous batch size per device = 2
[INFO|trainer.py:2420] 2025-04-30 22:55:30,692 >>   Total train batch size (w. parallel, distributed & accumulation) = 16
[INFO|trainer.py:2421] 2025-04-30 22:55:30,692 >>   Gradient Accumulation steps = 8
[INFO|trainer.py:2422] 2025-04-30 22:55:30,692 >>   Total optimization steps = 300
[INFO|trainer.py:2423] 2025-04-30 22:55:30,697 >>   Number of trainable parameters = 20,971,520
  2%|█▋                                                                                                | 5/300 [00:26<25:00,  5.09s/it][INFO|2025-04-30 22:55:57] llamafactory.train.callbacks:143 >> {'loss': 1.4229, 'learning_rate': 4.9978e-05, 'epoch': 0.17, 'throughput': 1828.21}
{'loss': 1.4229, 'grad_norm': 0.7704815864562988, 'learning_rate': 4.997807075247146e-05, 'epoch': 0.17, 'num_input_tokens_seen': 49104}
  3%|███▏                                                                                             | 10/300 [00:48<21:07,  4.37s/it][INFO|2025-04-30 22:56:19] llamafactory.train.callbacks:143 >> {'loss': 1.3738, 'learning_rate': 4.9889e-05, 'epoch': 0.33, 'throughput': 1833.79}
{'loss': 1.3738, 'grad_norm': 0.792708158493042, 'learning_rate': 4.9889049115077005e-05, 'epoch': 0.33, 'num_input_tokens_seen': 88640}
  5%|████▊                                                                                            | 15/300 [01:14<23:44,  5.00s/it][INFO|2025-04-30 22:56:45] llamafactory.train.callbacks:143 >> {'loss': 1.1926, 'learning_rate': 4.9732e-05, 'epoch': 0.50, 'throughput': 1844.52}
{'loss': 1.1926, 'grad_norm': 0.5519912242889404, 'learning_rate': 4.9731808324074717e-05, 'epoch': 0.5, 'num_input_tokens_seen': 137856}
  7%|██████▍                                                                                          | 20/300 [01:46<26:54,  5.77s/it][INFO|2025-04-30 22:57:16] llamafactory.train.callbacks:143 >> {'loss': 1.1809, 'learning_rate': 4.9507e-05, 'epoch': 0.66, 'throughput': 1847.60}
{'loss': 1.1809, 'grad_norm': 0.6411036849021912, 'learning_rate': 4.9506779365543046e-05, 'epoch': 0.66, 'num_input_tokens_seen': 196000}
  8%|████████                                                                                         | 25/300 [02:10<22:06,  4.83s/it][INFO|2025-04-30 22:57:41] llamafactory.train.callbacks:143 >> {'loss': 1.1651, 'learning_rate': 4.9215e-05, 'epoch': 0.83, 'throughput': 1842.25}
{'loss': 1.1651, 'grad_norm': 0.6471150517463684, 'learning_rate': 4.9214579028215776e-05, 'epoch': 0.83, 'num_input_tokens_seen': 240160}
 10%|█████████▋                                                                                       | 30/300 [02:37<24:13,  5.38s/it][INFO|2025-04-30 22:58:08] llamafactory.train.callbacks:143 >> {'loss': 1.1805, 'learning_rate': 4.8856e-05, 'epoch': 1.00, 'throughput': 1846.56}
{'loss': 1.1805, 'grad_norm': 0.641589879989624, 'learning_rate': 4.8856008212906925e-05, 'epoch': 1.0, 'num_input_tokens_seen': 291344}
 12%|███████████▎                                                                                     | 35/300 [02:58<21:26,  4.85s/it][INFO|2025-04-30 22:58:28] llamafactory.train.callbacks:143 >> {'loss': 1.2113, 'learning_rate': 4.8432e-05, 'epoch': 1.13, 'throughput': 1844.82}
{'loss': 1.2113, 'grad_norm': 0.7425159215927124, 'learning_rate': 4.843204973729729e-05, 'epoch': 1.13, 'num_input_tokens_seen': 328560}
 13%|████████████▉                                                                                    | 40/300 [03:25<22:02,  5.09s/it][INFO|2025-04-30 22:58:56] llamafactory.train.callbacks:143 >> {'loss': 0.9388, 'learning_rate': 4.7944e-05, 'epoch': 1.30, 'throughput': 1847.51}
{'loss': 0.9388, 'grad_norm': 0.6481277346611023, 'learning_rate': 4.794386564209953e-05, 'epoch': 1.3, 'num_input_tokens_seen': 380240}
 15%|██████████████▌                                                                                  | 45/300 [03:50<19:20,  4.55s/it][INFO|2025-04-30 22:59:20] llamafactory.train.callbacks:143 >> {'loss': 0.9767, 'learning_rate': 4.7393e-05, 'epoch': 1.46, 'throughput': 1848.03}
{'loss': 0.9767, 'grad_norm': 0.8144084215164185, 'learning_rate': 4.7392794005985326e-05, 'epoch': 1.46, 'num_input_tokens_seen': 425472}
 17%|████████████████▏                                                                                | 50/300 [04:15<21:26,  5.15s/it][INFO|2025-04-30 22:59:46] llamafactory.train.callbacks:143 >> {'loss': 0.9825, 'learning_rate': 4.6780e-05, 'epoch': 1.63, 'throughput': 1845.84}
{'loss': 0.9825, 'grad_norm': 0.6587629318237305, 'learning_rate': 4.678034527800474e-05, 'epoch': 1.63, 'num_input_tokens_seen': 471408}
 18%|████████████▋                                                        | 55/300 [04:42<22:39,  5.55s/it]                            [INFO|2025-04-30 23:00:13] llamafactory.train.callbacks:143 >> {'loss': 1.0046, 'learning_rate': 4.6108e-05, 'epoch': 1.80, 'throughput': 1847.78}
{'loss': 1.0046, 'grad_norm': 0.8781566023826599, 'learning_rate': 4.610819813755038e-05, 'epoch': 1.8, 'num_input_tokens_seen': 522240}
 20%|█████████████████████                                                                                    | 60/300 [05:09<20:15,  5.06s/it][INFO|2025-04-30 23:00:40] llamafactory.train.callbacks:143 >> {'loss': 0.9904, 'learning_rate': 4.5378e-05, 'epoch': 1.96, 'throughput': 1850.60}
{'loss': 0.9904, 'grad_norm': 1.3165696859359741, 'learning_rate': 4.537819489321386e-05, 'epoch': 1.96, 'num_input_tokens_seen': 573296}      
 22%|██████████████████████▊                                                                                  | 65/300 [05:32<19:51,  5.07s/it][INFO|2025-04-30 23:01:03] llamafactory.train.callbacks:143 >> {'loss': 0.7909, 'learning_rate': 4.4592e-05, 'epoch': 2.10, 'throughput': 1850.51}
{'loss': 0.7909, 'grad_norm': 0.7060555815696716, 'learning_rate': 4.4592336433146e-05, 'epoch': 2.1, 'num_input_tokens_seen': 615040}         
 23%|████████████████████████▌                                                                                | 70/300 [06:01<22:31,  5.87s/it][INFO|2025-04-30 23:01:32] llamafactory.train.callbacks:143 >> {'loss': 0.9185, 'learning_rate': 4.3753e-05, 'epoch': 2.27, 'throughput': 1851.58}
{'loss': 0.9185, 'grad_norm': 0.7310279607772827, 'learning_rate': 4.375277674076149e-05, 'epoch': 2.27, 'num_input_tokens_seen': 669008}      
 25%|██████████████████████████▎                                                                              | 75/300 [06:26<20:03,  5.35s/it][INFO|2025-04-30 23:01:57] llamafactory.train.callbacks:143 >> {'loss': 0.9117, 'learning_rate': 4.2862e-05, 'epoch': 2.43, 'throughput': 1848.75}
{'loss': 0.9117, 'grad_norm': 0.9736334681510925, 'learning_rate': 4.2861816990820084e-05, 'epoch': 2.43, 'num_input_tokens_seen': 714448}     
 27%|████████████████████████████                                                                             | 80/300 [06:49<18:00,  4.91s/it][INFO|2025-04-30 23:02:19] llamafactory.train.callbacks:143 >> {'loss': 0.8814, 'learning_rate': 4.1922e-05, 'epoch': 2.60, 'throughput': 1849.38}
{'loss': 0.8814, 'grad_norm': 0.841780424118042, 'learning_rate': 4.192189924206652e-05, 'epoch': 2.6, 'num_input_tokens_seen': 756640}        
 28%|█████████████████████████████▊                                                                           | 85/300 [07:18<20:36,  5.75s/it][INFO|2025-04-30 23:02:49] llamafactory.train.callbacks:143 >> {'loss': 0.8248, 'learning_rate': 4.0936e-05, 'epoch': 2.76, 'throughput': 1848.29}
{'loss': 0.8248, 'grad_norm': 0.7921251058578491, 'learning_rate': 4.093559974371725e-05, 'epoch': 2.76, 'num_input_tokens_seen': 810240}      
 30%|███████████████████████████████▌                                                                         | 90/300 [07:42<17:33,  5.02s/it][INFO|2025-04-30 23:03:13] llamafactory.train.callbacks:143 >> {'loss': 0.8703, 'learning_rate': 3.9906e-05, 'epoch': 2.93, 'throughput': 1847.69}
{'loss': 0.8703, 'grad_norm': 1.1796730756759644, 'learning_rate': 3.99056218741404e-05, 'epoch': 2.93, 'num_input_tokens_seen': 854880}       
 32%|█████████████████████████████████▎                                                                       | 95/300 [08:00<12:37,  3.69s/it][INFO|2025-04-30 23:03:30] llamafactory.train.callbacks:143 >> {'loss': 0.7647, 'learning_rate': 3.8835e-05, 'epoch': 3.07, 'throughput': 1846.54}
{'loss': 0.7647, 'grad_norm': 1.0020028352737427, 'learning_rate': 3.883478873108361e-05, 'epoch': 3.07, 'num_input_tokens_seen': 886576}      
 33%|██████████████████████████████████▋                                                                     | 100/300 [08:26<17:17,  5.19s/it][INFO|2025-04-30 23:03:56] llamafactory.train.callbacks:143 >> {'loss': 0.8376, 'learning_rate': 3.7726e-05, 'epoch': 3.23, 'throughput': 1845.50}
{'loss': 0.8376, 'grad_norm': 0.9901264309883118, 'learning_rate': 3.7726035393759285e-05, 'epoch': 3.23, 'num_input_tokens_seen': 934048}     
 33%|██████████████████████████████████▋                                                                     | 100/300 [08:26<17:17,  5.19s/it][INFO|trainer.py:4307] 2025-04-30 23:03:56,824 >> 
***** Running Evaluation *****
[INFO|trainer.py:4309] 2025-04-30 23:03:56,824 >>   Num examples = 10
[INFO|trainer.py:4312] 2025-04-30 23:03:56,824 >>   Batch size = 2
{'eval_loss': 0.8902555704116821, 'eval_runtime': 1.0888, 'eval_samples_per_second': 9.185, 'eval_steps_per_second': 4.592, 'epoch': 3.23, 'num_input_tokens_seen': 934048}                                                                                                                   
 33%|██████████████████████████████████▋                                                                     | 100/300 [08:27<17:17,  5.19s/it[INFO|trainer.py:3984] 2025-04-30 23:03:57,913 >> Saving model checkpoint to saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-100
[INFO|configuration_utils.py:691] 2025-04-30 23:03:57,940 >> loading configuration file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/config.json
[INFO|configuration_utils.py:765] 2025-04-30 23:03:57,941 >> Model config LlamaConfig {
  "architectures": [
    "LlamaForCausalLM"
  ],
  "attention_bias": false,
  "attention_dropout": 0.0,
  "bos_token_id": 128000,
  "eos_token_id": 128009,
  "head_dim": 128,
  "hidden_act": "silu",
  "hidden_size": 4096,
  "initializer_range": 0.02,
  "intermediate_size": 14336,
  "max_position_embeddings": 8192,
  "mlp_bias": false,
  "model_type": "llama",
  "num_attention_heads": 32,
  "num_hidden_layers": 32,
  "num_key_value_heads": 8,
  "pretraining_tp": 1,
  "rms_norm_eps": 1e-05,
  "rope_scaling": null,
  "rope_theta": 500000.0,
  "tie_word_embeddings": false,
  "torch_dtype": "bfloat16",
  "transformers_version": "4.51.3",
  "use_cache": true,
  "vocab_size": 128256
}

[INFO|tokenization_utils_base.py:2510] 2025-04-30 23:03:58,055 >> tokenizer config file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-100/tokenizer_config.json
[INFO|tokenization_utils_base.py:2519] 2025-04-30 23:03:58,056 >> Special tokens file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-100/special_tokens_map.json
 35%|████████████████████████████████████▍                                                                   | 105/300 [08:55<18:04,  5.56s/it][INFO|2025-04-30 23:04:26] llamafactory.train.callbacks:143 >> {'loss': 0.8084, 'learning_rate': 3.6582e-05, 'epoch': 3.40, 'throughput': 1840.54}
{'loss': 0.8084, 'grad_norm': 1.00861656665802, 'learning_rate': 3.6582400877996546e-05, 'epoch': 3.4, 'num_input_tokens_seen': 985920}        
 37%|██████████████████████████████████████▏                                                                 | 110/300 [09:20<16:18,  5.15s/it][INFO|2025-04-30 23:04:51] llamafactory.train.callbacks:143 >> {'loss': 0.7581, 'learning_rate': 3.5407e-05, 'epoch': 3.56, 'throughput': 1841.19}
{'loss': 0.7581, 'grad_norm': 1.3527833223342896, 'learning_rate': 3.540701980651003e-05, 'epoch': 3.56, 'num_input_tokens_seen': 1031952}     
 38%|███████████████████████████████████████▊                                                                | 115/300 [09:45<14:22,  4.66s/it][INFO|2025-04-30 23:05:16] llamafactory.train.callbacks:143 >> {'loss': 0.7432, 'learning_rate': 3.4203e-05, 'epoch': 3.73, 'throughput': 1840.62}
{'loss': 0.7432, 'grad_norm': 2.001084566116333, 'learning_rate': 3.4203113817116957e-05, 'epoch': 3.73, 'num_input_tokens_seen': 1077312}     
 40%|████████████████████████████████████▉                                                        | 119/300 [10:07<16:19,  5.41s/it] 40%|█████████████████████████████████████▏                                                       | 120/300 [10:14<17:20,  5.78s/it][INFO|2025-04-30 23:05:45] llamafactory.train.callbacks:143 >> {'loss': 0.7761, 'learning_rate': 3.2974e-05, 'epoch': 3.90, 'throughput': 1841.18}
{'loss': 0.7761, 'grad_norm': 1.1301747560501099, 'learning_rate': 3.2973982732451755e-05, 'epoch': 3.9, 'num_input_tokens_seen': 1131280}
 42%|██████████████████████████████████████▊                                                      | 125/300 [10:32<11:56,  4.10s/it][INFO|2025-04-30 23:06:02] llamafactory.train.callbacks:143 >> {'loss': 0.8311, 'learning_rate': 3.1723e-05, 'epoch': 4.03, 'throughput': 1840.62}
{'loss': 0.8311, 'grad_norm': 1.2576369047164917, 'learning_rate': 3.172299551538164e-05, 'epoch': 4.03, 'num_input_tokens_seen': 1163808}
 42%|██████████████████████▍                              | 127/300 [10:43<13:50,  4.80s/it] 43%|██████████████████████████████                                        | 129/300 [10:51<12:50,  4.51s/it] 43%|██████████████████████████████▎                                       | 130/300 [10:56<13:31,  4.77s/it][INFO|2025-04-30 23:06:27] llamafactory.train.callbacks:143 >> {'loss': 0.6267, 'learning_rate': 3.0454e-05, 'epoch': 4.20, 'throughput': 1840.47}
{'loss': 0.6267, 'grad_norm': 0.9211604595184326, 'learning_rate': 3.045358103491357e-05, 'epoch': 4.2, 'num_input_tokens_seen': 1209136}
 45%|███████████████████████████████▌                                      | 135/300 [11:24<14:46,  5.37s/it][INFO|2025-04-30 23:06:55] llamafactory.train.callbacks:143 >> {'loss': 0.7250, 'learning_rate': 2.9169e-05, 'epoch': 4.37, 'throughput': 1840.79}
{'loss': 0.725, 'grad_norm': 1.575364112854004, 'learning_rate': 2.916921866790256e-05, 'epoch': 4.37, 'num_input_tokens_seen': 1260512}
 47%|████████████████████████████████▋                                     | 140/300 [11:50<13:46,  5.17s/it][INFO|2025-04-30 23:07:20] llamafactory.train.callbacks:143 >> {'loss': 0.7194, 'learning_rate': 2.7873e-05, 'epoch': 4.53, 'throughput': 1840.79}
{'loss': 0.7194, 'grad_norm': 1.2807148694992065, 'learning_rate': 2.787342876232167e-05, 'epoch': 4.53, 'num_input_tokens_seen': 1307280}
 48%|█████████████████████████████████▊                                    | 145/300 [12:15<12:45,  4.94s/it][INFO|2025-04-30 23:07:46] llamafactory.train.callbacks:143 >> {'loss': 0.6322, 'learning_rate': 2.6570e-05, 'epoch': 4.70, 'throughput': 1841.04}
{'loss': 0.6322, 'grad_norm': 1.222657322883606, 'learning_rate': 2.656976298823284e-05, 'epoch': 4.7, 'num_input_tokens_seen': 1354192}
 50%|███████████████████████████████████                                   | 150/300 [12:44<13:35,  5.44s/it][INFO|2025-04-30 23:08:14] llamafactory.train.callbacks:143 >> {'loss': 0.6882, 'learning_rate': 2.5262e-05, 'epoch': 4.86, 'throughput': 1841.57}
{'loss': 0.6882, 'grad_norm': 1.4224449396133423, 'learning_rate': 2.5261794602906145e-05, 'epoch': 4.86, 'num_input_tokens_seen': 1407296}
 52%|████████████████████████████████████▏                                 | 155/300 [13:02<08:22,  3.46s/it][INFO|2025-04-30 23:08:33] llamafactory.train.callbacks:143 >> {'loss': 0.7161, 'learning_rate': 2.3953e-05, 'epoch': 5.00, 'throughput': 1840.54}
{'loss': 0.7161, 'grad_norm': 4.876495361328125, 'learning_rate': 2.3953108656770016e-05, 'epoch': 5.0, 'num_input_tokens_seen': 1440456}
 53%|█████████████████████████████████████▎                                | 160/300 [13:30<11:47,  5.06s/it][INFO|2025-04-30 23:09:01] llamafactory.train.callbacks:143 >> {'loss': 0.5989, 'learning_rate': 2.2647e-05, 'epoch': 5.17, 'throughput': 1841.08}
{'loss': 0.5989, 'grad_norm': 1.2173418998718262, 'learning_rate': 2.2647292167037144e-05, 'epoch': 5.17, 'num_input_tokens_seen': 1492296}
 55%|██████████████████████████████████████▌                               | 165/300 [13:55<10:51,  4.82s/it][INFO|2025-04-30 23:09:25] llamafactory.train.callbacks:143 >> {'loss': 0.6127, 'learning_rate': 2.1348e-05, 'epoch': 5.33, 'throughput': 1840.52}
{'loss': 0.6127, 'grad_norm': 1.6756207942962646, 'learning_rate': 2.1347924285939714e-05, 'epoch': 5.33, 'num_input_tokens_seen': 1536872}
 57%|███████████████████████████████████████▋                              | 170/300 [14:23<12:45,  5.89s/it][INFO|2025-04-30 23:09:54] llamafactory.train.callbacks:143 >> {'loss': 0.5737, 'learning_rate': 2.0059e-05, 'epoch': 5.50, 'throughput': 1840.69}
{'loss': 0.5737, 'grad_norm': 1.361559510231018, 'learning_rate': 2.0058566490521847e-05, 'epoch': 5.5, 'num_input_tokens_seen': 1590152}
 58%|████████████████████████████████████████▊                             | 175/300 [14:49<10:53,  5.23s/it][INFO|2025-04-30 23:10:19] llamafactory.train.callbacks:143 >> {'loss': 0.5896, 'learning_rate': 1.8783e-05, 'epoch': 5.66, 'throughput': 1840.70}
{'loss': 0.5896, 'grad_norm': 1.6987861394882202, 'learning_rate': 1.8782752820878634e-05, 'epoch': 5.66, 'num_input_tokens_seen': 1636680}
 60%|██████████████████████████████████████████                            | 180/300 [15:16<10:37,  5.32s/it][INFO|2025-04-30 23:10:47] llamafactory.train.callbacks:143 >> {'loss': 0.5659, 'learning_rate': 1.7524e-05, 'epoch': 5.83, 'throughput': 1840.94}
{'loss': 0.5659, 'grad_norm': 1.3385348320007324, 'learning_rate': 1.7523980193597836e-05, 'epoch': 5.83, 'num_input_tokens_seen': 1687864}
 62%|███████████████████████████████████████████▏                          | 185/300 [15:40<09:23,  4.90s/it][INFO|2025-04-30 23:11:11] llamafactory.train.callbacks:143 >> {'loss': 0.6106, 'learning_rate': 1.6286e-05, 'epoch': 6.00, 'throughput': 1840.24}
{'loss': 0.6106, 'grad_norm': 1.766627550125122, 'learning_rate': 1.6285698816954624e-05, 'epoch': 6.0, 'num_input_tokens_seen': 1731336}
 63%|████████████████████████████████████████████▎                         | 190/300 [16:01<08:48,  4.80s/it][INFO|2025-04-30 23:11:32] llamafactory.train.callbacks:143 >> {'loss': 0.5981, 'learning_rate': 1.5071e-05, 'epoch': 6.13, 'throughput': 1840.49}
{'loss': 0.5981, 'grad_norm': 1.474120020866394, 'learning_rate': 1.5071302734130489e-05, 'epoch': 6.13, 'num_input_tokens_seen': 1770000}
 65%|█████████████████████████████████████████████▌                        | 195/300 [16:31<10:25,  5.96s/it][INFO|2025-04-30 23:12:02] llamafactory.train.callbacks:143 >> {'loss': 0.5465, 'learning_rate': 1.3884e-05, 'epoch': 6.30, 'throughput': 1840.67}
{'loss': 0.5465, 'grad_norm': 1.9015015363693237, 'learning_rate': 1.388412052037682e-05, 'epoch': 6.3, 'num_input_tokens_seen': 1825792}
 67%|██████████████████████████████████████████████▋                       | 200/300 [16:54<07:57,  4.77s/it][INFO|2025-04-30 23:12:25] llamafactory.train.callbacks:143 >> {'loss': 0.5196, 'learning_rate': 1.2727e-05, 'epoch': 6.46, 'throughput': 1839.63}
{'loss': 0.5196, 'grad_norm': 1.598514437675476, 'learning_rate': 1.272740615962148e-05, 'epoch': 6.46, 'num_input_tokens_seen': 1866144}
 67%|██████████████████████████████████████████████▋                       | 200/300 [16:54<07:57,  4.77s/it][INFO|trainer.py:4307] 2025-04-30 23:12:25,116 >> 
***** Running Evaluation *****
[INFO|trainer.py:4309] 2025-04-30 23:12:25,116 >>   Num examples = 10
[INFO|trainer.py:4312] 2025-04-30 23:12:25,116 >>   Batch size = 2
{'eval_loss': 0.9802400469779968, 'eval_runtime': 1.0888, 'eval_samples_per_second': 9.185, 'eval_steps_per_second': 4.592, 'epoch': 6.46, 'num_input_tokens_seen': 1866144}                                              
 67%|██████████████████████████████████████████████▋                       | 200/300 [16:55<07:57,  4.77s/it[INFO|trainer.py:3984] 2025-04-30 23:12:26,205 >> Saving model checkpoint to saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-200
[INFO|configuration_utils.py:691] 2025-04-30 23:12:26,231 >> loading configuration file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/config.json
[INFO|configuration_utils.py:765] 2025-04-30 23:12:26,232 >> Model config LlamaConfig {
  "architectures": [
    "LlamaForCausalLM"
  ],
  "attention_bias": false,
  "attention_dropout": 0.0,
  "bos_token_id": 128000,
  "eos_token_id": 128009,
  "head_dim": 128,
  "hidden_act": "silu",
  "hidden_size": 4096,
  "initializer_range": 0.02,
  "intermediate_size": 14336,
  "max_position_embeddings": 8192,
  "mlp_bias": false,
  "model_type": "llama",
  "num_attention_heads": 32,
  "num_hidden_layers": 32,
  "num_key_value_heads": 8,
  "pretraining_tp": 1,
  "rms_norm_eps": 1e-05,
  "rope_scaling": null,
  "rope_theta": 500000.0,
  "tie_word_embeddings": false,
  "torch_dtype": "bfloat16",
  "transformers_version": "4.51.3",
  "use_cache": true,
  "vocab_size": 128256
}

[INFO|tokenization_utils_base.py:2510] 2025-04-30 23:12:26,342 >> tokenizer config file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-200/tokenizer_config.json
[INFO|tokenization_utils_base.py:2519] 2025-04-30 23:12:26,342 >> Special tokens file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-200/special_tokens_map.json
 68%|███████████████████████████████████████████████▊                      | 205/300 [17:22<08:05,  5.11s/it][INFO|2025-04-30 23:12:52] llamafactory.train.callbacks:143 >> {'loss': 0.5030, 'learning_rate': 1.1604e-05, 'epoch': 6.63, 'throughput': 1837.16}
{'loss': 0.503, 'grad_norm': 1.6823495626449585, 'learning_rate': 1.1604330125525079e-05, 'epoch': 6.63, 'num_input_tokens_seen': 1914768}
 70%|█████████████████████████████████████████████████                     | 210/300 [17:48<07:36,  5.08s/it][INFO|2025-04-30 23:13:19] llamafactory.train.callbacks:143 >> {'loss': 0.5119, 'learning_rate': 1.0518e-05, 'epoch': 6.80, 'throughput': 1837.43}
{'loss': 0.5119, 'grad_norm': 1.9426920413970947, 'learning_rate': 1.0517970691433035e-05, 'epoch': 6.8, 'num_input_tokens_seen': 1963792}
 72%|██████████████████████████████████████████████████▏                   | 215/300 [18:16<07:20,  5.18s/it][INFO|2025-04-30 23:13:46] llamafactory.train.callbacks:143 >> {'loss': 0.5338, 'learning_rate': 9.4713e-06, 'epoch': 6.96, 'throughput': 1837.76}
{'loss': 0.5338, 'grad_norm': 2.2999331951141357, 'learning_rate': 9.471305493042243e-06, 'epoch': 6.96, 'num_input_tokens_seen': 2014640}
 73%|███████████████████████████████████████████████████▎                  | 220/300 [18:36<05:49,  4.37s/it][INFO|2025-04-30 23:14:06] llamafactory.train.callbacks:143 >> {'loss': 0.4328, 'learning_rate': 8.4672e-06, 'epoch': 7.10, 'throughput': 1837.53}
{'loss': 0.4328, 'grad_norm': 1.8626716136932373, 'learning_rate': 8.467203366908707e-06, 'epoch': 7.1, 'num_input_tokens_seen': 2050752}
 75%|████████████████████████████████████████████████████▌                 | 225/300 [19:04<06:50,  5.47s/it][INFO|2025-04-30 23:14:35] llamafactory.train.callbacks:143 >> {'loss': 0.5297, 'learning_rate': 7.5084e-06, 'epoch': 7.27, 'throughput': 1838.20}
{'loss': 0.5297, 'grad_norm': 1.7538148164749146, 'learning_rate': 7.508416487165862e-06, 'epoch': 7.27, 'num_input_tokens_seen': 2104336}
 77%|█████████████████████████████████████████████████████▋                | 230/300 [19:26<05:38,  4.84s/it][INFO|2025-04-30 23:14:57] llamafactory.train.callbacks:143 >> {'loss': 0.3744, 'learning_rate': 6.5976e-06, 'epoch': 7.43, 'throughput': 1837.92}
{'loss': 0.3744, 'grad_norm': 1.717400312423706, 'learning_rate': 6.5975728220066425e-06, 'epoch': 7.43, 'num_input_tokens_seen': 2144400}
 78%|██████████████████████████████████████████████████████▊               | 235/300 [19:54<06:14,  5.76s/it][INFO|2025-04-30 23:15:25] llamafactory.train.callbacks:143 >> {'loss': 0.4758, 'learning_rate': 5.7372e-06, 'epoch': 7.60, 'throughput': 1838.40}
{'loss': 0.4758, 'grad_norm': 1.7317731380462646, 'learning_rate': 5.737168930605272e-06, 'epoch': 7.6, 'num_input_tokens_seen': 2195984}
 80%|████████████████████████████████████████████████████████              | 240/300 [20:20<05:21,  5.36s/it][INFO|2025-04-30 23:15:51] llamafactory.train.callbacks:143 >> {'loss': 0.4812, 'learning_rate': 4.9296e-06, 'epoch': 7.76, 'throughput': 1838.26}
{'loss': 0.4812, 'grad_norm': 1.8744982481002808, 'learning_rate': 4.929563120222141e-06, 'epoch': 7.76, 'num_input_tokens_seen': 2243936}
 82%|█████████████████████████████████████████████████████████▏            | 245/300 [20:49<05:05,  5.55s/it][INFO|2025-04-30 23:16:19] llamafactory.train.callbacks:143 >> {'loss': 0.4890, 'learning_rate': 4.1770e-06, 'epoch': 7.93, 'throughput': 1838.92}
{'loss': 0.489, 'grad_norm': 1.7645634412765503, 'learning_rate': 4.176968982247514e-06, 'epoch': 7.93, 'num_input_tokens_seen': 2297088}
 83%|██████████████████████████████████████████████████████████▎           | 250/300 [21:08<03:34,  4.28s/it][INFO|2025-04-30 23:16:39] llamafactory.train.callbacks:143 >> {'loss': 0.4780, 'learning_rate': 3.4814e-06, 'epoch': 8.07, 'throughput': 1839.08}
{'loss': 0.478, 'grad_norm': 1.8085341453552246, 'learning_rate': 3.4814493249014116e-06, 'epoch': 8.07, 'num_input_tokens_seen': 2332760}
 85%|███████████████████████████████████████████████████████████▌          | 255/300 [21:38<04:07,  5.51s/it][INFO|2025-04-30 23:17:09] llamafactory.train.callbacks:143 >> {'loss': 0.5098, 'learning_rate': 2.8449e-06, 'epoch': 8.23, 'throughput': 1839.75}
{'loss': 0.5098, 'grad_norm': 1.5961071252822876, 'learning_rate': 2.8449105192196316e-06, 'epoch': 8.23, 'num_input_tokens_seen': 2389688}
 87%|████████████████████████████████████████████████████████████▋         | 260/300 [22:08<03:46,  5.65s/it][INFO|2025-04-30 23:17:38] llamafactory.train.callbacks:143 >> {'loss': 0.4861, 'learning_rate': 2.2691e-06, 'epoch': 8.40, 'throughput': 1840.22}
{'loss': 0.4861, 'grad_norm': 1.6634273529052734, 'learning_rate': 2.269097273823287e-06, 'epoch': 8.4, 'num_input_tokens_seen': 2443880}
 88%|█████████████████████████████████████████████████████████████▊        | 265/300 [22:33<02:50,  4.88s/it][INFO|2025-04-30 23:18:04] llamafactory.train.callbacks:143 >> {'loss': 0.4825, 'learning_rate': 1.7556e-06, 'epoch': 8.56, 'throughput': 1840.31}
{'loss': 0.4825, 'grad_norm': 1.9975656270980835, 'learning_rate': 1.7555878527937164e-06, 'epoch': 8.56, 'num_input_tokens_seen': 2491672}
 90%|███████████████████████████████████████████████████████████████       | 270/300 [23:01<02:47,  5.60s/it][INFO|2025-04-30 23:18:32] llamafactory.train.callbacks:143 >> {'loss': 0.3529, 'learning_rate': 1.3058e-06, 'epoch': 8.73, 'throughput': 1840.48}
{'loss': 0.3529, 'grad_norm': 1.5703303813934326, 'learning_rate': 1.305789749760361e-06, 'epoch': 8.73, 'num_input_tokens_seen': 2542712}
 92%|████████████████████████████████████████████████████████████████▏     | 275/300 [23:25<02:06,  5.07s/it][INFO|2025-04-30 23:18:56] llamafactory.train.callbacks:143 >> {'loss': 0.4455, 'learning_rate': 9.2094e-07, 'epoch': 8.90, 'throughput': 1840.05}
{'loss': 0.4455, 'grad_norm': 1.8864351511001587, 'learning_rate': 9.209358300585474e-07, 'epoch': 8.9, 'num_input_tokens_seen': 2586360}
 93%|█████████████████████████████████████████████████████████████████▎    | 280/300 [23:44<01:18,  3.90s/it][INFO|2025-04-30 23:19:15] llamafactory.train.callbacks:143 >> {'loss': 0.3613, 'learning_rate': 6.0208e-07, 'epoch': 9.03, 'throughput': 1840.16}
{'loss': 0.3613, 'grad_norm': 1.9168891906738281, 'learning_rate': 6.020809515313142e-07, 'epoch': 9.03, 'num_input_tokens_seen': 2621072}
 95%|██████████████████████████████████████████████████████████████████▌   | 285/300 [24:08<01:08,  4.58s/it][INFO|2025-04-30 23:19:39] llamafactory.train.callbacks:143 >> {'loss': 0.4072, 'learning_rate': 3.5010e-07, 'epoch': 9.20, 'throughput': 1839.71}
{'loss': 0.4072, 'grad_norm': 1.7348805665969849, 'learning_rate': 3.5009907323737825e-07, 'epoch': 9.2, 'num_input_tokens_seen': 2665168}
 97%|███████████████████████████████████████████████████████████████████▋  | 290/300 [24:34<00:45,  4.54s/it][INFO|2025-04-30 23:20:04] llamafactory.train.callbacks:143 >> {'loss': 0.3553, 'learning_rate': 1.6568e-07, 'epoch': 9.37, 'throughput': 1839.82}
{'loss': 0.3553, 'grad_norm': 2.031686782836914, 'learning_rate': 1.6568085999008888e-07, 'epoch': 9.37, 'num_input_tokens_seen': 2712256}
 98%|████████████████████████████████████████████████████████████████████▊ | 295/300 [25:01<00:26,  5.23s/it][INFO|2025-04-30 23:20:32] llamafactory.train.callbacks:143 >> {'loss': 0.5210, 'learning_rate': 4.9332e-08, 'epoch': 9.53, 'throughput': 1839.80}
{'loss': 0.521, 'grad_norm': 2.0482985973358154, 'learning_rate': 4.9331789293211026e-08, 'epoch': 9.53, 'num_input_tokens_seen': 2762736}
100%|██████████████████████████████████████████████████████████████████████| 300/300 [25:28<00:00,  5.18s/it][INFO|2025-04-30 23:20:59] llamafactory.train.callbacks:143 >> {'loss': 0.4480, 'learning_rate': 1.3708e-09, 'epoch': 9.70, 'throughput': 1840.12}
{'loss': 0.448, 'grad_norm': 1.621444582939148, 'learning_rate': 1.3707658621964215e-09, 'epoch': 9.7, 'num_input_tokens_seen': 2812704}
100%|██████████████████████████████████████████████████████████████████████| 300/300 [25:28<00:00,  5.18s/it][INFO|trainer.py:4307] 2025-04-30 23:20:59,243 >> 
***** Running Evaluation *****
[INFO|trainer.py:4309] 2025-04-30 23:20:59,244 >>   Num examples = 10
[INFO|trainer.py:4312] 2025-04-30 23:20:59,244 >>   Batch size = 2
{'eval_loss': 1.0198612213134766, 'eval_runtime': 1.0896, 'eval_samples_per_second': 9.178, 'eval_steps_per_second': 4.589, 'epoch': 9.7, 'num_input_tokens_seen': 2812704}                                               
100%|██████████████████████████████████████████████████████████████████████| 300/300 [25:29<00:00,  5.18s/it[INFO|trainer.py:3984] 2025-04-30 23:21:00,334 >> Saving model checkpoint to saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-300
[INFO|configuration_utils.py:691] 2025-04-30 23:21:00,360 >> loading configuration file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/config.json
[INFO|configuration_utils.py:765] 2025-04-30 23:21:00,361 >> Model config LlamaConfig {
  "architectures": [
    "LlamaForCausalLM"
  ],
  "attention_bias": false,
  "attention_dropout": 0.0,
  "bos_token_id": 128000,
  "eos_token_id": 128009,
  "head_dim": 128,
  "hidden_act": "silu",
  "hidden_size": 4096,
  "initializer_range": 0.02,
  "intermediate_size": 14336,
  "max_position_embeddings": 8192,
  "mlp_bias": false,
  "model_type": "llama",
  "num_attention_heads": 32,
  "num_hidden_layers": 32,
  "num_key_value_heads": 8,
  "pretraining_tp": 1,
  "rms_norm_eps": 1e-05,
  "rope_scaling": null,
  "rope_theta": 500000.0,
  "tie_word_embeddings": false,
  "torch_dtype": "bfloat16",
  "transformers_version": "4.51.3",
  "use_cache": true,
  "vocab_size": 128256
}

[INFO|tokenization_utils_base.py:2510] 2025-04-30 23:21:00,463 >> tokenizer config file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-300/tokenizer_config.json
[INFO|tokenization_utils_base.py:2519] 2025-04-30 23:21:00,463 >> Special tokens file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/checkpoint-300/special_tokens_map.json
[INFO|trainer.py:2681] 2025-04-30 23:21:00,837 >> 

Training completed. Do not forget to share your model on huggingface.co/models =)


{'train_runtime': 1530.1399, 'train_samples_per_second': 3.144, 'train_steps_per_second': 0.196, 'train_loss': 0.7139853564898173, 'epoch': 9.7, 'num_input_tokens_seen': 2812704}
100%|██████████████████████████████████████████████████████████████████████| 300/300 [25:30<00:00,  5.10s/it]
[INFO|trainer.py:3984] 2025-04-30 23:21:00,838 >> Saving model checkpoint to saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03
[INFO|configuration_utils.py:691] 2025-04-30 23:21:00,864 >> loading configuration file /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct/config.json
[INFO|configuration_utils.py:765] 2025-04-30 23:21:00,865 >> Model config LlamaConfig {
  "architectures": [
    "LlamaForCausalLM"
  ],
  "attention_bias": false,
  "attention_dropout": 0.0,
  "bos_token_id": 128000,
  "eos_token_id": 128009,
  "head_dim": 128,
  "hidden_act": "silu",
  "hidden_size": 4096,
  "initializer_range": 0.02,
  "intermediate_size": 14336,
  "max_position_embeddings": 8192,
  "mlp_bias": false,
  "model_type": "llama",
  "num_attention_heads": 32,
  "num_hidden_layers": 32,
  "num_key_value_heads": 8,
  "pretraining_tp": 1,
  "rms_norm_eps": 1e-05,
  "rope_scaling": null,
  "rope_theta": 500000.0,
  "tie_word_embeddings": false,
  "torch_dtype": "bfloat16",
  "transformers_version": "4.51.3",
  "use_cache": true,
  "vocab_size": 128256
}

[INFO|tokenization_utils_base.py:2510] 2025-04-30 23:21:00,967 >> tokenizer config file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/tokenizer_config.json
[INFO|tokenization_utils_base.py:2519] 2025-04-30 23:21:00,967 >> Special tokens file saved in saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/special_tokens_map.json
***** train metrics *****
  epoch                    =      9.6971
  num_input_tokens_seen    =     2812704
  total_flos               = 118286078GF
  train_loss               =       0.714
  train_runtime            =  0:25:30.13
  train_samples_per_second =       3.144
  train_steps_per_second   =       0.196
Figure saved at: saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/training_loss.png
Figure saved at: saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03/training_eval_loss.png
[WARNING|2025-04-30 23:21:01] llamafactory.extras.ploting:148 >> No metric eval_accuracy to plot.
[INFO|trainer.py:4307] 2025-04-30 23:21:01,357 >> 
***** Running Evaluation *****
[INFO|trainer.py:4309] 2025-04-30 23:21:01,357 >>   Num examples = 10
[INFO|trainer.py:4312] 2025-04-30 23:21:01,357 >>   Batch size = 2
100%|██████████████████████████████████████████████████████████████████████████| 5/5 [00:00<00:00,  6.60it/s]
***** eval metrics *****
  epoch                   =     9.6971
  eval_loss               =     1.0199
  eval_runtime            = 0:00:01.08
  eval_samples_per_second =       9.18
  eval_steps_per_second   =       4.59
  num_input_tokens_seen   =    2812704
[INFO|modelcard.py:450] 2025-04-30 23:21:02,448 >> Dropping the following result as it does not have all the necessary fields:
{'task': {'name': 'Causal Language Modeling', 'type': 'text-generation'}}

```

模型合并：将训练出来的两个低秩矩阵和基座模型进行合并

指令：

```shell
# 进入llamafactory目录下，创建cust文件夹，并新建一个yaml合并配置
llamafactory-cli export merge_llama3_lora_sft.yaml
```

```yaml
# 基座模型地址
model_name_or_path: /root/autodl-tmp/LLaMA-Factory/saves/Llama-3-8B-Instruct
# lora低秩矩阵
adapter_name_or_path: /root/autodl-tmp/LLaMA-Factory/saves/Llama-3-8B-Instruct/lora/train_2025-04-30-22-47-03
# 基座模型类型
template: llama
#微调模式
finetuning_type: lora

# export导出相关配置
export_dir: /root/autodl-tmp/Llama3-8B/LLM-Research/Meta-Llama-3-8B-Instruct-merge
# 导出的文件个数（将大模型按照个数进行均分）
export_size: 4
# 导出模型的使用设备平台（cpu/cuda）
export_device: cuda
# 导出格式：false为新格式
export_legacy_format: false
```

模型量化：通过牺牲精度的方式，来提高模型在推理的性能

> 模型量化过程中为什么需要用到量化校准数据？因为量化降低了每一层权重的**存储精度**，使得模型的计算复杂度降低，但也导致了可能出现推理的错误，所以需要通过校准数据来对量化前后的数据变化进行比对。

分类模型指标：精确率、召回率、混淆矩阵

大语言模型评估方法：模型评估是判断模型训练是否有效的方法
1. 主观评估：寻找领域专家，让专家提供一些测试问题，根据模型给出的答案进行判断模型是否有效
2. 客观评估：通过模型的评估**测试集**（非训练集数据），让训练好的模型去处理这批训练集数据，并根据输出与测试集标签进行打分。根据得分输出评估结果。

大语言模型评估指标：
- 精度指标：
  - BLEU-4（准确/精确率）：专门用于评估生成模型的生成质量得分，得分范围是[0, 1]，得分越大质量越好
      > BLEU可以根据n-gram划分成多种评价指标，其中n-gram代表测试单词的连续数，像BLEU-4就是4个单词
  - ROUGE（召回率）：指在机器翻译、自动摘要、问答生成等领域常见的评估指标。其会将模型生成的摘要或者回答与参考答案进行比较计算。
    - ROUGE-1
    - ROUGE-2
    - ROUGE-L
- 性能指标：
  - model_prepare_time（模型准备时间）
  - runtime（总运行时间）
  - samples_per_second（每秒处理样本数）
  - steps_per_second(每秒处理步骤数)

什么时候用微调？什么时候用RAG？

微调场景：
1. 当需要改变大模型生成语句的话术/语气/回答方式
2. 当需要改变模型的自我认知能力，如模型对自身基础的认知

RAG：
1. 适用于根据用户的提问进行某个相关领域专业回答的模式

本质就是知识库是**动态更新**的，但是大模型是使用某一个时间点之前的数据进行训练的**静态模型**。所以使用哪种技术要通过具体场景的静、动态属性进行权衡，最佳的实践可以两者兼之（如训练一个某公司的具有某细分专业领域的智能客服）

RAG选取模型参数大小：1.8B～7B之间，根据知识库的数据量来定，数据量越低模型参数可以越少